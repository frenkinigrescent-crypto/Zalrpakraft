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
        r.playToClient(OpenMapPayload.TYPE, OpenMapPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.zalrpakraft.client.ClientHooks.openMap(p.mapJson(), p.progress());
        }));
        r.playToClient(PlaquePayload.TYPE, PlaquePayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (FMLEnvironment.dist.isClient()) dev.zalrpakraft.client.PlaqueOverlay.push(p.id(), p.icon(), p.frame());
        }));
        r.playBidirectional(SimplePayload.TYPE, SimplePayload.CODEC, ZNetwork::handleSimple);
    }

    private static void handleSimple(SimplePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                if ("journal_read".equals(p.action())) {
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
