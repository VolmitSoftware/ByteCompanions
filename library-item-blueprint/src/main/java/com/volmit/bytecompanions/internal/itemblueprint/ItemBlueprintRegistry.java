package com.volmit.bytecompanions.internal.itemblueprint;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.internal.itemblueprint.base.CompiledBase;
import com.volmit.bytecompanions.internal.itemblueprint.base.ItemBaseRegistry;
import com.volmit.bytecompanions.internal.itemblueprint.base.builtin.MaterialBase;
import com.volmit.bytecompanions.internal.itemblueprint.base.builtin.SkullBase;
import com.volmit.bytecompanions.internal.itemblueprint.common.CommonFieldsParser;
import com.volmit.bytecompanions.internal.itemblueprint.component.CompiledComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.ItemComponentRegistry;
import com.volmit.bytecompanions.internal.itemblueprint.component.builtin.AttributesComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.builtin.BannerPatternsComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.builtin.CustomDataComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.builtin.DyedColorComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.builtin.EnchantmentsComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.builtin.PotionComponent;
import com.volmit.bytecompanions.internal.itemblueprint.component.builtin.TrimComponent;
import com.volmit.bytecompanions.internal.itemblueprint.text.TextSerializer;
import com.volmit.bytecompanions.internal.itemblueprint.text.TextSerializers;

import java.lang.System.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ItemBlueprintRegistry {

    private static final Logger LOGGER = System.getLogger("item-blueprint");

    private final @NotNull ItemBaseRegistry bases = new ItemBaseRegistry();
    private final @NotNull ItemComponentRegistry components = new ItemComponentRegistry();
    private final @NotNull TextSerializer textSerializer;

    private ItemBlueprintRegistry(final @NotNull TextSerializer textSerializer) {
        this.textSerializer = textSerializer;
        this.registerDefaults();
    }

    @Contract(" -> new")
    public static @NotNull ItemBlueprintRegistry create() {
        return new ItemBlueprintRegistry(TextSerializers.miniMessage());
    }

    @Contract("_ -> new")
    public static @NotNull ItemBlueprintRegistry create(final @NotNull TextSerializer textSerializer) {
        return new ItemBlueprintRegistry(textSerializer);
    }

    private void registerDefaults() {
        // Bases
        this.bases.register("material", new MaterialBase());
        this.bases.register("skull", new SkullBase());

        // Components
        this.components.register("enchantments", new EnchantmentsComponent());
        this.components.register("attributes", new AttributesComponent());
        this.components.register("trim", new TrimComponent());
        this.components.register("dyed-color", new DyedColorComponent());
        this.components.register("banner-patterns", new BannerPatternsComponent());
        this.components.register("potion", new PotionComponent());
        this.components.register("custom-data", new CustomDataComponent());
    }

    public void clear() {
        this.bases.clear();
        this.components.clear();
    }

    public @NotNull ItemBaseRegistry bases() {
        return this.bases;
    }

    public @NotNull ItemComponentRegistry components() {
        return this.components;
    }

    public @NotNull BlueprintResponse<ItemBlueprint> compile(final @NotNull ConfigurationNode itemNode) {
        final BlueprintResponse<CompiledBase> baseResponse = this.bases.compile(itemNode);
        if (!baseResponse.isSuccess()) {
            return baseResponse.propagate("");
        }

        final BlueprintResponse<CompiledComponent> commonFieldsResponse = CommonFieldsParser.compile(itemNode, this.textSerializer);
        if (!commonFieldsResponse.isSuccess()) {
            return commonFieldsResponse.propagate("");
        }

        final BlueprintResponse<List<CompiledComponent>> componentsResponse = this.compileComponents(itemNode.node("components"));
        if (!componentsResponse.isSuccess()) {
            return componentsResponse.propagate("components");
        }

        return BlueprintResponse.success(new ItemBlueprint(
                baseResponse.value(),
                commonFieldsResponse.value(),
                componentsResponse.value()
        ));
    }

    private @NotNull BlueprintResponse<List<CompiledComponent>> compileComponents(final @NotNull ConfigurationNode componentsNode) {
        final List<CompiledComponent> compiled = new ArrayList<>();
        if (componentsNode.virtual()) {
            return BlueprintResponse.success(List.of());
        }

        for (final Map.Entry<Object, ? extends ConfigurationNode> entry : componentsNode.childrenMap().entrySet()) {
            final String id = String.valueOf(entry.getKey());
            final BlueprintResponse<CompiledComponent> response = this.components.compile(id, entry.getValue());

            if (!response.isSuccess()) {
                if (response.result() == BlueprintResult.UNKNOWN_TYPE) {
                    LOGGER.log(Logger.Level.WARNING, "Skipping unknown item component '" + id + "' (is the owning plugin loaded?).");
                    continue;
                }

                return response.propagate(id);
            }

            compiled.add(response.value());
        }

        return BlueprintResponse.success(List.copyOf(compiled));
    }

}
