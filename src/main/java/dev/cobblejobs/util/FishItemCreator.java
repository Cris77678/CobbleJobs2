package dev.cobblejobs.util;

import dev.cobblejobs.job.minigame.ResistanceFishingMinigame.FishRarity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class FishItemCreator {

    /**
     * Crea un ItemStack representando un pez capturado con sus estadísticas.
     * Basado en la lógica original de FishItem de la V1.
     */
    public static ItemStack create(String species, double weight, int length, boolean shiny, FishRarity rarity) {
        // Usamos Bacalao como base (se puede cambiar por un ítem de Cobblemon si existe)
        ItemStack stack = new ItemStack(Items.COD);

        // Configurar nombre visual
        String name = (shiny ? "§6✨ " : "") + rarity.displayName + " " + formatName(species);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));

        // Crear los datos internos (NBT dentro de componentes en 1.21)
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Species", species);
        nbt.putDouble("Weight", weight);
        nbt.putInt("Length", length);
        nbt.putBoolean("IsShiny", shiny);
        nbt.putString("Rarity", rarity.name());
        nbt.putBoolean("IsCobbleFish", true);

        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        // Añadir Lore (descripción del ítem)
        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("§7Especie: §f" + formatName(species)));
        lore.add(Text.literal("§7Peso: §e" + String.format("%.2f", weight) + " kg"));
        lore.add(Text.literal("§7Longitud: §e" + length + " cm"));
        if (shiny) lore.add(Text.literal("§6§l¡VARIOCOLOR!"));
        
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(lore));

        return stack;
    }

    private static String formatName(String species) {
        String name = species.replace("cobblemon:", "").replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
