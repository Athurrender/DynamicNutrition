package com.chillpavz.dynamicnutrition.command;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import com.chillpavz.dynamicnutrition.Constants;
import com.chillpavz.dynamicnutrition.DynamicNutrition;
import com.chillpavz.dynamicnutrition.nutrition.Nutrient;
import com.chillpavz.dynamicnutrition.nutrition.Nutrients;
import com.chillpavz.dynamicnutrition.config.NutritionConfig;
import com.chillpavz.dynamicnutrition.nutrition.MealHistory;
import com.chillpavz.dynamicnutrition.nutrition.NutritionOrigin;
import com.chillpavz.dynamicnutrition.nutrition.NutritionTable;
import com.chillpavz.dynamicnutrition.nutrition.NutritionValues;
import com.chillpavz.dynamicnutrition.platform.Services;
import com.chillpavz.dynamicnutrition.player.PlayerNutrition;

/**
 * {@code /dynamicnutrition}.
 *
 * <p><b>Permission level matters here.</b> A setter left open to any player is a creative-mode
 * switch for everybody on a server, which is a mistake this kind of mod has shipped before.
 * Everything that inspects is open; nothing that changes state is.
 *
 * <p>{@code unassigned} exists because the startup warning already tells people to run it, and
 * because it is independently the most asked for diagnostic in mods that resolve values this way.
 * A food resolving to nothing is this mod's defining silent failure, so the tool that lists them is
 * not a nicety.
 */
public final class NutritionCommands {

    private NutritionCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Constants.MOD_ID)
                // Reading is harmless, so it stays open to everyone.
                .then(Commands.literal("get").executes(NutritionCommands::get))
                .then(Commands.literal("unassigned").executes(NutritionCommands::unassigned))
                .then(Commands.literal("report").executes(NutritionCommands::report))
                .then(Commands.literal("variety").executes(NutritionCommands::variety))
                // Writing a file to the server directory is not "reading", so it needs an operator.
                // Everything below CHANGES a player's values, so it is operator only. A setter
                // open to everyone is, on a server, a creative mode switch for the whole player
                // list; that has been shipped in this field before and reported as such.
                .then(Commands.literal("set")
                        .requires(NutritionCommands::isOperator)
                        .then(Commands.argument("nutrient", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (Nutrient n : Nutrients.all()) {
                                        b.suggest(n.name());
                                    }
                                    b.suggest("all");
                                    return b.buildFuture();
                                })
                                .then(Commands.argument("value",
                                                IntegerArgumentType.integer(0, (int) PlayerNutrition.MAX))
                                        .executes(NutritionCommands::set))))
                .then(Commands.literal("forget")
                        .requires(NutritionCommands::isOperator)
                        .executes(NutritionCommands::forget))
                .then(Commands.literal("drain")
                        .requires(NutritionCommands::isOperator)
                        .executes(ctx -> setAll(ctx, 0)))
                .then(Commands.literal("fill")
                        .requires(NutritionCommands::isOperator)
                        .executes(ctx -> setAll(ctx, (int) PlayerNutrition.MAX)))
                .then(Commands.literal("export")
                        // Integer permission levels are GONE at 26.x: hasPermission(int) does not
                        // exist and the levels are PermissionCheck objects tested against the
                        // source's own PermissionSet.
                        .requires(source ->
                                source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(NutritionCommands::export)));
    }

    /**
     * Integer permission levels are GONE at 26.x: {@code hasPermission(int)} does not exist and the
     * levels are {@code PermissionCheck} objects tested against the source's own set.
     */
    private static boolean isOperator(CommandSourceStack source) {
        return source.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }

    /**
     * Set one nutrient, or all of them with the name "all".
     *
     * <p>These exist because the status effects are otherwise very hard to test: reaching
     * malnourished honestly means ignoring food for several in-game days, and the transitions are
     * exactly the part most worth checking.
     */
    private static int set(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "nutrient");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        if ("all".equalsIgnoreCase(name)) {
            return setAll(ctx, value);
        }
        Nutrient nutrient = Nutrients.byName(name);
        if (nutrient == null) {
            ctx.getSource().sendFailure(Component.literal("No such nutrient: " + name));
            return 0;
        }
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        PlayerNutrition nutrition = Services.STORAGE.get(player);
        nutrition.set(nutrient, value);
        Services.STORAGE.markDirty(player);
        ctx.getSource().sendSuccess(() -> Component.literal(name + " = " + value), false);
        return 1;
    }

    private static int setAll(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                              int value) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        PlayerNutrition nutrition = Services.STORAGE.get(player);
        for (Nutrient nutrient : Nutrients.all()) {
            nutrition.set(nutrient, value);
        }
        Services.STORAGE.markDirty(player);
        ctx.getSource().sendSuccess(
                () -> Component.literal("every nutrient = " + value), false);
        return 1;
    }

    private static ServerPlayer playerOrNull(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("This command needs a player."));
            return null;
        }
    }

    private static int get(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("This command needs a player."));
            return 0;
        }
        PlayerNutrition nutrition = Services.STORAGE.get(player);
        for (Nutrient nutrient : Nutrients.all()) {
            int value = nutrition.display(nutrient);
            ctx.getSource().sendSuccess(() -> Component.translatable(nutrient.translationKey())
                    .append(Component.literal(": " + value + " / " + (int) PlayerNutrition.MAX
                            + " (" + nutrition.status(nutrient).name().toLowerCase() + ")")), false);
        }
        return 1;
    }

    /**
     * List every food the pipeline could not resolve.
     *
     * <p>Runs on the server, where the recipe walk is possible; a client could not answer this even
     * in principle, because it cannot enumerate recipes.
     */
    private static int unassigned(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        List<String> empty = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            try {
                if (!NutritionTable.isEdible(item)) {
                    continue;
                }
                NutritionValues values = DynamicNutrition.table().resolve(level, item);
                if (values.isEmpty()) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                    empty.add(id == null ? item.toString() : id.toString());
                }
            } catch (Throwable ignored) {
                // One awkward item must not stop the report.
            }
        }
        if (empty.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("Every food resolves to at least one nutrient."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                empty.size() + " food item(s) resolve to no nutrients:"), false);
        for (String id : empty) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + id), false);
            Constants.LOG.info("Unassigned food: {}", id);
        }
        return empty.size();
    }

    /**
     * Write the whole resolved table to a CSV.
     *
     * <p>The reason to have this is not the players: modpack developers are the people who
     * recommend mods to other people, and a table they can open in a spreadsheet is what lets them
     * check the balance of their own pack.
     *
     * <p>The derivation is included too. A pack author's real question is not
     * "what is this worth" but "why does the mod think that", and the source column answers it for
     * every row at once.
     */
    private static int export(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        // Sorted by id, so two exports of the same pack diff cleanly against each other.
        Map<String, Item> foods = new TreeMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            try {
                if (!NutritionTable.isEdible(item)) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                foods.put(id == null ? item.toString() : id.toString(), item);
            } catch (Throwable ignored) {
                // One awkward item must not cost the whole export.
            }
        }

        Path out = ctx.getSource().getServer().getFile(Constants.MOD_ID + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".csv");
        int written = 0;
        try {
            Files.createDirectories(out.getParent());
            try (Writer w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                w.write("item,nutrition,saturation");
                for (Nutrient nutrient : Nutrients.all()) {
                    w.write("," + nutrient.name());
                }
                w.write(",source,derived_from" + System.lineSeparator());

                for (Map.Entry<String, Item> entry : foods.entrySet()) {
                    NutritionValues values = DynamicNutrition.table().resolve(level, entry.getValue());
                    NutritionOrigin origin = DynamicNutrition.table().origin(entry.getValue());
                    var food = entry.getValue().getDefaultInstance().get(DataComponents.FOOD);
                    StringBuilder row = new StringBuilder(entry.getKey());
                    row.append(',').append(food == null ? 0 : food.nutrition());
                    row.append(',').append(food == null ? 0.0F : food.saturation());
                    for (Nutrient nutrient : Nutrients.all()) {
                        row.append(',').append(values.get(nutrient));
                    }
                    row.append(',').append(origin.source().name().toLowerCase());
                    row.append(',');
                    // Space separated, so no cell ever needs quoting and the file stays trivially
                    // parseable by anything.
                    for (int i = 0; i < origin.from().size(); i++) {
                        if (i > 0) {
                            row.append(' ');
                        }
                        ResourceLocation from = BuiltInRegistries.ITEM.getKey(origin.from().get(i));
                        row.append(from == null ? "?" : from.toString());
                    }
                    w.write(row.append(System.lineSeparator()).toString());
                    written++;
                }
            }
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Could not write the export: " + e));
            Constants.LOG.warn("Nutrition export failed", e);
            return 0;
        }
        int count = written;
        ctx.getSource().sendSuccess(
                () -> Component.literal("Wrote " + count + " foods to " + out.getFileName()), true);
        Constants.LOG.info("Wrote the nutrition table for {} foods to {}", count, out);
        return count;
    }

    /**
     * What the player has been eating, and what their held food is currently worth.
     *
     * <p>Reading only, so it stays open to everyone. The variety multiplier is the one number in
     * this mod a player cannot work out for themselves, and "why is this food worth less than it
     * says" is exactly the question that gets a mechanic reported as a bug.
     */
    private static int variety(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        PlayerNutrition nutrition = Services.STORAGE.get(player);
        List<String> meals = nutrition.meals();
        int window = Math.max(1, NutritionConfig.varietyWindow);
        List<String> recent = meals.subList(Math.max(0, meals.size() - window), meals.size());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "last " + recent.size() + " meal(s), " + Set.copyOf(recent).size()
                        + " different: " + String.join(", ", recent)), false);

        Item held = player.getMainHandItem().getItem();
        if (NutritionTable.isEdible(held)) {
            float mult = MealHistory.multiplier(meals, held);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "held food would be worth " + Math.round(mult * 100) + "%"), false);
        }
        return recent.size();
    }

    /** Wipe the meal history. Testing only, hence operator. */
    private static int forget(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) {
            return 0;
        }
        Services.STORAGE.get(player).forgetMeals();
        Services.STORAGE.markDirty(player);
        ctx.getSource().sendSuccess(() -> Component.literal("meal history cleared"), false);
        return 1;
    }

    /** Counts, for a quick "is this thing working" check without spamming chat. */
    private static int report(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        var table = DynamicNutrition.table();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "explicit entries: " + table.explicitSize()
                        + ", resolved: " + table.cacheSize()
                        + ", prewarmed: " + table.isPrewarmed()), false);
        return 1;
    }
}
