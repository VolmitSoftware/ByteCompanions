package com.volmit.bytecompanions.internal.itemblueprint;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BlueprintResponse<T> {

    @NotNull BlueprintResult result();

    @Nullable T value();

    @NotNull String path();

    @NotNull String message();

    default boolean isSuccess() {
        return this.result().isSuccess();
    }

    default @Nullable T orElseThrow() {
        if (!this.isSuccess()) {
            throw new IllegalStateException("[" + this.path() + "] " + this.message());
        }

        return this.value();
    }

    @Contract("_ -> new")
    default <O> @NotNull BlueprintResponse<O> propagate(final @NotNull String pathPrefix) {
        return BlueprintResponse.error(this.result(), pathPrefix + (this.path().isEmpty() ? "" : "." + this.path()), this.message());
    }

    @Contract("_ -> new")
    static <T> @NotNull BlueprintResponse<T> success(final @NotNull T value) {
        return new Response<>(BlueprintResult.SUCCESS, value, "", "");
    }

    @Contract("_, _, _ -> new")
    static <T> @NotNull BlueprintResponse<T> error(
            final @NotNull BlueprintResult result,
            final @NotNull String path,
            final @NotNull String message
    ) {
        return new Response<>(result, null, path, message);
    }

    record Response<T>(
            @NotNull BlueprintResult result,
            @Nullable T value,
            @NotNull String path,
            @NotNull String message
    ) implements BlueprintResponse<T> { }

}
