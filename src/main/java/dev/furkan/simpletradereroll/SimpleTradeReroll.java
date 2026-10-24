package dev.furkan.simpletradereroll;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** The integrated-server half of the mod. Never touch villager data off the server thread. */
public final class SimpleTradeReroll implements ModInitializer {
    public enum TargetKind { ENCHANTMENT, RESULT_ITEM, PAYMENT_ITEM }
    public record Target(TargetKind kind, String id, String label, int level, int maxEmeraldCost) {
        public Component summary() {
            MutableComponent result = Component.literal(label);
            if (kind == TargetKind.ENCHANTMENT && level > 0) {
                result.append(Component.translatable("text.simple_trade_reroll.level_suffix", level));
            }
            if (maxEmeraldCost > 0) {
                result.append(Component.translatable("text.simple_trade_reroll.emerald_suffix", maxEmeraldCost));
            }
            return result;
        }
    }
    private record Job(UUID villagerId, Target target, int attempts) {}

    public static final int MAX_ATTEMPTS = 2000;
    private static final Map<UUID, Job> JOBS = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(SimpleTradeReroll::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> JOBS.clear());
    }

    public static boolean isRunning(UUID playerId) {
        return JOBS.containsKey(playerId);
    }

    /** -1 means no active search. Safe for the client thread to read. */
    public static int attempts(UUID playerId) {
        Job job = JOBS.get(playerId);
        return job == null ? -1 : job.attempts();
    }

    public static void manual(ServerPlayer player) {
        JOBS.remove(player.getUUID());
        Villager villager = currentVillager(player);
        if (villager == null || !canReroll(player, villager)) return;
        if (reroll(player, villager)) {
            say(player, "message.simple_trade_reroll.refreshed");
        }
    }

    public static void stopAuto(ServerPlayer player) {
        if (JOBS.remove(player.getUUID()) != null) say(player, "message.simple_trade_reroll.stopped");
    }

    public static void startAuto(ServerPlayer player, Target target) {
        JOBS.remove(player.getUUID());
        Villager villager = currentVillager(player);
        if (villager == null || !canReroll(player, villager)) return;
        if (target == null || Identifier.tryParse(target.id()) == null
                || target.level() < 0 || target.maxEmeraldCost() < 0) {
            say(player, "message.simple_trade_reroll.choose_target");
            return;
        }
        if (matches(villager.getOffers(), target)) {
            say(player, "message.simple_trade_reroll.already_found");
            return;
        }
        JOBS.put(player.getUUID(), new Job(villager.getUUID(), target, 0));
        say(player, "message.simple_trade_reroll.searching", target.summary());
    }

    private static void tick(MinecraftServer server) {
        for (var entry : JOBS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Job job = entry.getValue();
            if (player == null) {
                JOBS.remove(entry.getKey(), job);
                continue;
            }
            Villager villager = currentVillager(player);
            if (villager == null || !villager.getUUID().equals(job.villagerId()) || !canReroll(player, villager)) {
                JOBS.remove(entry.getKey(), job);
                continue;
            }
            // Two rolls per tick keep the interface responsive without a tight, unbounded loop.
            for (int i = 0; i < 2; i++) {
                if (!reroll(player, villager)) {
                    JOBS.remove(entry.getKey(), job);
                    break;
                }
                int attempts = job.attempts() + i + 1;
                if (matches(villager.getOffers(), job.target())) {
                    JOBS.remove(entry.getKey());
                    say(player, "message.simple_trade_reroll.found", job.target().summary(), attempts);
                    break;
                }
                if (attempts >= MAX_ATTEMPTS) {
                    JOBS.remove(entry.getKey());
                    say(player, "message.simple_trade_reroll.not_found", MAX_ATTEMPTS);
                    break;
                }
                if (i == 1) JOBS.replace(entry.getKey(), job, new Job(job.villagerId(), job.target(), attempts));
            }
        }
    }

    private static Villager currentVillager(ServerPlayer player) {
        if (!(player.containerMenu instanceof MerchantMenu menu) || !menu.stillValid(player)) {
            say(player, "message.simple_trade_reroll.open_trade");
            return null;
        }
        return player.level().getEntitiesOfClass(Villager.class,
                player.getBoundingBox().inflate(8.0),
                v -> v.getTradingPlayer() == player && v.distanceToSqr(player) <= 64.0)
                .stream().findFirst().orElseGet(() -> {
                    say(player, "message.simple_trade_reroll.no_villager");
                    return null;
                });
    }

    private static boolean canReroll(ServerPlayer player, Villager villager) {
        MerchantMenu menu = (MerchantMenu) player.containerMenu;
        if (!menu.getSlot(0).getItem().isEmpty() || !menu.getSlot(1).getItem().isEmpty()) {
            say(player, "message.simple_trade_reroll.clear_inputs");
            return false;
        }
        if (villager.getVillagerData().level() != 1 || villager.getVillagerXp() != 0
                || villager.getOffers().stream().anyMatch(offer -> offer.getUses() > 0)) {
            say(player, "message.simple_trade_reroll.locked");
            return false;
        }
        return true;
    }

    private static boolean reroll(ServerPlayer player, Villager villager) {
        MerchantOffers oldOffers = villager.getOffers();
        villager.setOffers(null); // Vanilla getOffers() rebuilds offers through updateTrades().
        MerchantOffers offers = villager.getOffers();
        if (offers.isEmpty()) {
            villager.setOffers(oldOffers);
            say(player, "message.simple_trade_reroll.no_offers");
            return false;
        }
        MerchantMenu menu = (MerchantMenu) player.containerMenu;
        player.sendMerchantOffers(menu.containerId, offers,
                villager.getVillagerData().level(), villager.getVillagerXp(), true, menu.canRestock());
        return true;
    }

    private static boolean matches(MerchantOffers offers, Target target) {
        Identifier id = Identifier.tryParse(target.id());
        if (id == null) return false;
        for (MerchantOffer offer : offers) {
            if (!matchesEmeraldCost(offer, target)) continue;
            if (target.kind() == TargetKind.ENCHANTMENT) {
                ItemEnchantments enchants = offer.getResult().get(DataComponents.STORED_ENCHANTMENTS);
                if (enchants != null && enchants.keySet().stream().anyMatch(holder ->
                        holder.is(id) && (target.level() == 0 || enchants.getLevel(holder) == target.level()))) {
                    return true;
                }
            } else if (target.kind() == TargetKind.RESULT_ITEM) {
                if (itemIs(offer.getResult(), id)) return true;
            } else if (itemIs(offer.getCostA(), id) || itemIs(offer.getCostB(), id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesEmeraldCost(MerchantOffer offer, Target target) {
        if (target.maxEmeraldCost() == 0) return true;
        int emeralds = 0;
        if (offer.getCostA().is(Items.EMERALD)) emeralds += offer.getCostA().getCount();
        if (offer.getCostB().is(Items.EMERALD)) emeralds += offer.getCostB().getCount();
        return emeralds > 0 && emeralds <= target.maxEmeraldCost();
    }

    private static boolean itemIs(ItemStack stack, Identifier id) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    private static void say(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(Component.literal("[Trade Reroll] ").append(Component.translatable(key, args)));
    }
}
