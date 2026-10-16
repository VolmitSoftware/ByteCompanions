import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:path/path.dart' as path;

import '../../ServerMultiplexor/MultiplexorApp/lib/services/rcon_client.dart';

Future<void> main(List<String> arguments) async {
  Map<String, String> options = <String, String>{};
  for (int i = 0; i < arguments.length; i += 2) {
    if (i + 1 == arguments.length || !arguments[i].startsWith('--')) {
      throw ArgumentError(
        'Use --instance <path> --mode exercise|restart|cleanup|diagnostics --report <json>',
      );
    }
    options[arguments[i].substring(2)] = arguments[i + 1];
  }
  ConsoleLifecycle runner = ConsoleLifecycle(options);
  await runner.run();
}

class ConsoleLifecycle {
  ConsoleLifecycle(this.options);

  final Map<String, String> options;
  final RconConnectionPool pool = RconConnectionPool();
  final List<Map<String, Object?>> checks = <Map<String, Object?>>[];
  final List<Map<String, Object?>> commands = <Map<String, Object?>>[];
  final DateTime started = DateTime.now().toUtc();
  late String instance;
  late String data;
  late String password;
  late int port;
  String? artifactHash;
  String? failure;

  Future<void> run() async {
    try {
      await validate();
      await step('runtime and native adapter ready', () async {
        String report = await diagnostic();
        requireReady(report);
      });
      await step('no players connected', () async {
        String response = await command('minecraft:list');
        require(
          RegExp(r'There are 0 of a max of').hasMatch(response),
          'Console lifecycle requires an empty server',
        );
      });
      switch (options['mode']) {
        case 'diagnostics':
          await step('relocated H2 archive preserves resources, syntax messages and I/O causes', () async {
            String response = await command('bcqa h2diagnostics');
            require(response.contains('BCQA H2_DIAGNOSTICS PASS')
                && response.contains('/.libs/')
                && response.contains('/relocated/ByteCompanions/h2-2.4.240.jar!/com/volmit/bytecompanions/libs/h2/util/data.zip')
                && RegExp(r'nestedSha256=[a-f0-9]{64} entries=97').hasMatch(response)
                && RegExp(r'syntaxCode=4200[01] ioCode=90031 cause=true').hasMatch(response),
                'H2 diagnostic fixture did not confirm relocated archive, messages and preserved cause: $response');
          });
        case 'exercise':
          await exercise();
        case 'restart':
          await step(
            'saved remote decoration restored after externally managed restart',
            () async {
              await verifySavedDecoration();
              requireReady(await diagnostic(), decorations: 1);
            },
          );
        case 'cleanup':
          await cleanup();
        default:
          throw ArgumentError('Mode must be exercise, restart, cleanup, or diagnostics');
      }
    } catch (error, stack) {
      failure = '$error\n$stack';
      exitCode = 1;
    } finally {
      pool.disposeAll();
      File reportFile = File(
        options['report'] ?? (throw ArgumentError('--report is required')),
      );
      await reportFile.parent.create(recursive: true);
      await reportFile.writeAsString(
        const JsonEncoder.withIndent('  ').convert(<String, Object?>{
          'scope': 'console lifecycle only; no client connection, gameplay, rendering, or packet delivery proof',
          'mode': options['mode'],
          'instance': options['instance'],
          'startedAt': started.toIso8601String(),
          'finishedAt': DateTime.now().toUtc().toIso8601String(),
          'artifactSha256': artifactHash,
          'passed': failure == null,
          'checks': checks,
          'commands': commands,
          'failure': failure,
        }),
      );
      stdout.writeln(
        '${failure == null ? "PASS" : "FAIL"}: ${checks.where((Map<String, Object?> check) => check['passed'] == true).length} console checks; ${reportFile.path}',
      );
    }
  }

  Future<void> validate() async {
    instance = await Directory(
      options['instance'] ?? (throw ArgumentError('--instance is required')),
    ).resolveSymbolicLinks();
    String workspace = await Directory.fromUri(
      Platform.script.resolve('../../ServerMultiplexor/'),
    ).resolveSymbolicLinks();
    require(
      path.isWithin(workspace, instance),
      'Instance must be inside the local ServerMultiplexor workspace',
    );
    require(
      path.basename(instance).startsWith('bytecompanions-'),
      'Instance must use the owned bytecompanions- QA prefix',
    );
    String source = await File(path.join(instance, '.server-source'))
        .readAsString();
    require(
      RegExp(r'^isolated=true\s*$', multiLine: true).hasMatch(source),
      'Instance must be isolated',
    );
    Map<String, String> properties = <String, String>{};
    for (String line in await File(
      path.join(instance, 'server.properties'),
    ).readAsLines()) {
      int separator = line.indexOf('=');
      if (separator > 0 && !line.startsWith('#'))
        properties[line.substring(0, separator).trim()] = line
            .substring(separator + 1)
            .trim();
    }
    require(
      properties['server-ip'] == '127.0.0.1',
      'Server must bind to IPv4 loopback',
    );
    require(
      properties['enable-rcon'] == 'true',
      'RCON must already be enabled',
    );
    require(
      properties['pause-when-empty-seconds'] == '-1',
      'Empty server pausing must be disabled',
    );
    password = properties['rcon.password'] ?? '';
    port = int.tryParse(properties['rcon.port'] ?? '') ?? 0;
    require(
      password.isNotEmpty && port > 0 && port < 65536,
      'RCON credentials and port must be configured',
    );
    data = path.join(instance, 'plugins', 'ByteCompanions');
    require(
      options['mode'] == 'diagnostics' || RegExp(r'autoReload\s*=\s*true')
          .hasMatch(await File(path.join(data, 'config.toml')).readAsString()),
      'Use autoReload=true for file reload checks',
    );
    artifactHash = sha256
        .convert(
          await File(path.join(instance, 'plugins', 'ByteCompanions.jar'))
              .readAsBytes(),
        )
        .toString();
  }

  Future<String> command(String value) async {
    String? response = await pool.command(
      '127.0.0.1',
      port,
      password,
      value,
      timeout: const Duration(seconds: 5),
    );
    commands.add(<String, Object?>{
      'at': DateTime.now().toUtc().toIso8601String(),
      'command': value,
      'response': response,
      'received': response != null,
    });
    require(
      response != null,
      'RCON connection, authentication, or response timed out for $value',
    );
    return response!;
  }

  Future<String> diagnostic() async {
    Directory directory = Directory(path.join(data, 'debug'));
    Set<String> previous = await reportPaths(directory);
    await command('companions debug');
    DateTime requestedAt = DateTime.now();
    String? result;
    await waitFor('new local diagnostic report', () async {
      for (String file in (await reportPaths(directory)).difference(previous)) {
        String contents = await File(file).readAsString();
        if (!contents.contains('ByteCompanions runtime') ||
            !contents.contains('Feature particles:'))
          continue;
        result = contents.substring(contents.indexOf('ByteCompanions runtime')).split('\n== ')[0];
        commands.add(<String, Object?>{
          'diagnosticFile': file,
          'pluginReport': result,
        });
        return true;
      }
      if (DateTime.now().difference(requestedAt).inMilliseconds >= 1500) {
        await command('companions debug');
        requestedAt = DateTime.now();
      }
      return false;
    });
    return result!;
  }

  Future<Set<String>> reportPaths(Directory directory) async {
    if (!await directory.exists()) return <String>{};
    return (await directory.list().toList())
        .whereType<File>()
        .where((File file) => file.path.endsWith('.txt'))
        .map((File file) => file.path)
        .toSet();
  }

  void requireReady(String report, {int? decorations}) {
    require(report.contains('Active: true'), 'Runtime is inactive');
    require(
      RegExp(r'Native adapter: (?!unknown|null)\S+').hasMatch(report),
      'Native adapter is unavailable',
    );
    require(
      report.contains('Loaded players: 0'),
      'Unexpected loaded player state',
    );
    if (decorations != null)
      require(
        report.contains('Active decorations: $decorations\n'),
        'Expected $decorations active decorations',
      );
  }

  int generation(String report) =>
      int.parse(RegExp(r'Generation: (\d+)').firstMatch(report)!.group(1)!);

  Future<void> reload(
    int previousGeneration, {
    required int decorations,
  }) async {
    File root = File(path.join(data, 'config.toml'));
    String contents = await root.readAsString();
    await root.writeAsString('$contents\n# QA file refresh ${DateTime.now().microsecondsSinceEpoch}\n', flush: true);
    await waitFor('active replacement runtime', () async {
      String report = await diagnostic();
      if (!report.contains('Active: true') ||
          generation(report) <= previousGeneration)
        return false;
      requireReady(report, decorations: decorations);
      return true;
    });
  }

  Future<void> exercise() async {
    requireReady(await diagnostic(), decorations: 0);
    File decoration = File(
      path.join(data, 'features', 'decorations', 'config.toml'),
    );
    File backup = File(
      path.join(instance, '.bytecompanions-console-decoration-original.toml'),
    );
    require(
      !await backup.exists(),
      'A previous exercise backup exists; run cleanup before a new exercise',
    );
    String original = await decoration.readAsString();
    await backup.writeAsString(original, flush: true);
    String rootConfig = await File(path.join(data, 'config.toml'))
        .readAsString();
    File root = File(path.join(data, 'config.toml'));
    try {
      await step(
        'remote decoration initializes on reload without viewers',
        () async {
          int before = generation(await diagnostic());
          await decoration.writeAsString(
            '''[settings]
update-interval = 3
view-distance = 32
view-refresh-delay = 3

[decorations.console_lifecycle]
companion = "bee"
world = "world"
x = 40000.0
y = 150.0
z = 40000.0
''',
          );
          await reload(before, decorations: 1);
        },
      );
      await step(
        'invalid root config preserves active runtime and decoration',
        () async {
          int before = generation(await diagnostic());
          File log = File(path.join(instance, 'logs', 'latest.log'));
          int offset = (await log.readAsString()).length;
          try {
            await root.writeAsString('invalid = [unterminated\n');
            await waitFor(
              'invalid file change rejection',
              () async => RegExp('Configuration hotload rejected|Could not reload ByteCompanions', caseSensitive: false)
                  .hasMatch((await log.readAsString()).substring(offset)),
            );
            String report = await diagnostic();
            requireReady(report, decorations: 1);
            require(
              generation(report) == before,
              'Invalid content replaced the accepted runtime',
            );
          } finally {
            await root.writeAsString(rootConfig, flush: true);
          }
        },
      );
      await step(
        'valid reload replaces runtime and keeps saved decoration',
        () async {
          await reload(generation(await diagnostic()), decorations: 1);
          await verifySavedDecoration();
        },
      );
    } catch (_) {
      await decoration.writeAsString(original, flush: true);
      rethrow;
    } finally {
      await root.writeAsString(rootConfig, flush: true);
    }
  }

  Future<void> verifySavedDecoration() async {
    require(
      await File(
        path.join(instance, '.bytecompanions-console-decoration-original.toml'),
      ).exists(),
      'The preceding exercise backup is missing',
    );
    String contents = await File(
      path.join(data, 'features', 'decorations', 'config.toml'),
    ).readAsString();
    require(
      contents.contains('console_lifecycle:') &&
          contents.contains('x: 40000.0') &&
          contents.contains('z: 40000.0'),
      'Remote decoration is not persisted',
    );
  }

  Future<void> cleanup() async {
    await step(
      'restore original decoration configuration and reload',
      () async {
        File backup = File(
          path.join(
            instance,
            '.bytecompanions-console-decoration-original.toml',
          ),
        );
        require(await backup.exists(), 'Exercise backup is missing');
        int before = generation(await diagnostic());
        await File(path.join(data, 'features', 'decorations', 'config.toml'))
            .writeAsString(await backup.readAsString(), flush: true);
        await reload(before, decorations: 0);
        await backup.delete();
      },
    );
  }

  Future<void> step(String name, Future<void> Function() action) async {
    try {
      await action();
      checks.add(<String, Object?>{'name': name, 'passed': true});
    } catch (error) {
      checks.add(<String, Object?>{
        'name': name,
        'passed': false,
        'error': '$error',
      });
      rethrow;
    }
  }

  Future<void> waitFor(String label, Future<bool> Function() predicate) async {
    DateTime deadline = DateTime.now().add(const Duration(seconds: 45));
    while (DateTime.now().isBefore(deadline)) {
      if (await predicate()) return;
      await Future<void>.delayed(const Duration(milliseconds: 750));
    }
    throw StateError('Timed out waiting for $label');
  }

  void require(bool condition, String message) {
    if (!condition) throw StateError(message);
  }
}
