package dev.zalrpakraft.net;

import dev.zalrpakraft.player.PlayerProgress;
import dev.zalrpakraft.player.ProgressService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ZNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(SyncProgressPayload.TYPE, SyncProgressPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.zalrpakraft.client.ClientHooks.onSync(p.tag());
        }));
        r.playToClient(TreeViewPayload.TYPE, TreeViewPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.zalrpakraft.client.ClientHooks.onTreeView(p.view(), p.json(), p.mapJson(), p.progress());
        }));
        r.playToClient(StarSyncPayload.TYPE, StarSyncPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.zalrpakraft.client.ClientTree.apply(p.tag());
        }));
        r.playToClient(PlaquePayload.TYPE, PlaquePayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.zalrpakraft.client.PlaqueOverlay.push(p.id(), p.icon(), p.frame());
        }));
        r.playToClient(QuestBookPayload.TYPE, QuestBookPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.zalrpakraft.client.ClientHooks.openQuests(p.bookJson(), p.state());
        }));
        r.playBidirectional(SimplePayload.TYPE, SimplePayload.CODEC, ZNetwork::handleSimple);
    }

    private static void handleSimple(SimplePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                String a = p.action();
                if (a.equals("selftest_setup") && !System.getProperty("zalrpakraft.clienttest", "").isEmpty()) {
                    PlayerProgress pr = ProgressService.of(sp);
                    pr.journalRead = pr.houseFound = pr.tableOpened = true;
                    pr.starPoints += 8;
                    var t = dev.zalrpakraft.tree.TreeBuilder.tree();
                    var big = t.systems.values().stream().max(java.util.Comparator.comparingInt(dev.zalrpakraft.tree.Tree.SystemDef::size)).orElse(null);
                    if (big != null) {
                        int k = 0;
                        for (String id : big.planets.get(0).nodes) {
                            var n = t.nodes.get(id);
                            if (n.parents.isEmpty() && k++ < 4) pr.unlockedItems.add(id);
                        }
                        for (String id : big.planets.get(0).nodes) {
                            var n = t.nodes.get(id);
                            if (!n.parents.isEmpty() && pr.unlockedItems.contains(n.parents.get(0)) && k++ < 7) pr.unlockedItems.add(id);
                        }
                    }
                    pr.starExp = 64;
                    ProgressService.sync(sp);
                    dev.zalrpakraft.tree.StarService.openGalaxy(sp);
                } else if (a.startsWith("tree_system:")) {
                    dev.zalrpakraft.tree.StarService.openSystem(sp, a.substring(12));
                } else if (a.startsWith("unlock:")) {
                    dev.zalrpakraft.tree.StarService.unlock(sp, a.substring(7));
                } else if (a.equals("open_map")) {
                    PlayerProgress pr = ProgressService.of(sp);
                    if (!pr.tableOpened) {
                        sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("zalrpakraft.map.need_table")
                                .withStyle(net.minecraft.ChatFormatting.GRAY), true);
                    } else {
                        pr.mapKeyUsed = true;
                        ProgressService.openMap(sp);
                        dev.zalrpakraft.player.QuestService.check(sp);
                    }
                } else if (a.equals("open_quests")) {
                    dev.zalrpakraft.player.QuestService.check(sp);
                    dev.zalrpakraft.player.QuestService.openBook(sp);
                } else if (a.startsWith("claim:")) {
                    dev.zalrpakraft.player.QuestService.claim(sp, a.substring(6));
                } else if (a.startsWith("track:")) {
                    dev.zalrpakraft.player.QuestService.track(sp, a.substring(6));
                } else if ("journal_read".equals(a)) {
                    PlayerProgress pr = ProgressService.of(sp);
                    if (!pr.journalRead) {
                        pr.journalRead = true;
                        dev.zalrpakraft.player.Achievements.award(sp, "journal");
                        ProgressService.sync(sp);
                    }
                }
            } else if (FMLEnvironment.dist.isClient()) {
                dev.zalrpakraft.client.ClientHooks.onSignal(p.action());
            }
        });
    }

    private ZNetwork() {}
}
