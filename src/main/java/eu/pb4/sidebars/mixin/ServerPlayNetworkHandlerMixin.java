package eu.pb4.sidebars.mixin;

import eu.pb4.sidebars.impl.SidebarAPIMod;
import eu.pb4.sidebars.api.SidebarInterface;
import eu.pb4.sidebars.api.lines.ImmutableSidebarLine;
import eu.pb4.sidebars.api.lines.SidebarLine;
import eu.pb4.sidebars.impl.SidebarHolder;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("UnreachableCode")
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin extends ServerCommonNetworkHandler implements SidebarHolder {

    @Unique
    private final Set<SidebarInterface> sidebarApi$sidebars = new HashSet<>();
    @Unique
    private final ImmutableSidebarLine[] sidebarApi$lines = new ImmutableSidebarLine[15];
    @Unique
    private SidebarInterface sidebarApi$currentSidebar = null;
    @Unique
    private Text sidebarApi$title = null;
    @Unique
    private boolean sidebarApi$alreadyHidden = true;

    @Unique int sidebarApi$currentTick = 0;

    public ServerPlayNetworkHandlerMixin(MinecraftServer server, ClientConnection connection, ConnectedClientData clientData) {
        super(server, connection, clientData);
    }

    @Inject(method = "onDisconnected", at = @At("TAIL"))
    private void sidebarApi$removeFromSidebars(DisconnectionInfo info, CallbackInfo ci) {
        var handler = (ServerPlayNetworkHandler) (Object) this;
        for (SidebarInterface sidebar : new HashSet<>(this.sidebarApi$sidebars)) {
            sidebar.disconnected(handler);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sidebarApi$updateSidebar(CallbackInfo ci) {
        this.sidebarApi$updateState(true);
    }

    @Override
    public void sidebarApi$removeEmpty() {
        if (!this.sidebarApi$alreadyHidden) {
            this.sidebarApi$alreadyHidden = true;
            {
                ScoreboardDisplayS2CPacket packet = new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, null);
                this.sendPacket(packet);
            }

            {
                ScoreboardObjectiveUpdateS2CPacket packet = new ScoreboardObjectiveUpdateS2CPacket( SidebarAPIMod.SCOREBOARD_OBJECTIVE, 1);
                this.sendPacket(packet);
            }
        }
    }

    @Override
    public void sidebarApi$setupInitial() {
        if (this.sidebarApi$alreadyHidden) {
            var handler = (ServerPlayNetworkHandler) (Object) this;

            this.sidebarApi$alreadyHidden = false;
            this.sidebarApi$title = this.sidebarApi$currentSidebar.getTitleFor(handler);
            this.sidebarApi$currentTick = 0;
            
            {
                ScoreboardObjectiveUpdateS2CPacket packet = new ScoreboardObjectiveUpdateS2CPacket(SidebarAPIMod.SCOREBOARD_OBJECTIVE, 0);
                SOUS2CPacketAccessor accessor = (SOUS2CPacketAccessor) packet;
                accessor.setTitle(this.sidebarApi$title);
                this.sendPacket(packet);
            }
            {
                ScoreboardDisplayS2CPacket packet = new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, null);
                ScoreboardDisplayS2CPacketAccessor accessor = (ScoreboardDisplayS2CPacketAccessor) packet;
                accessor.setName(SidebarAPIMod.OBJECTIVE_NAME);
                this.sendPacket(packet);
            }

            int x = 0;
            for (SidebarLine line : this.sidebarApi$currentSidebar.getLinesFor(handler)) {
                this.sidebarApi$lines[x] = line.immutableCopy(handler);
                this.sendPacket(new ScoreboardScoreUpdateS2CPacket(
                        SidebarAPIMod.FAKE_PLAYER_NAMES.get(x), SidebarAPIMod.OBJECTIVE_NAME, line.getValue(), Optional.of(line.getText(handler)), Optional.ofNullable(line.getNumberFormat(handler))));
                x++;
            }
        }
    }

    @Override
    public void sidebarApi$updateText() {
        var handler = (ServerPlayNetworkHandler) (Object) this;

        Text sidebarTitle = this.sidebarApi$currentSidebar.getTitleFor(handler);
        if (!sidebarTitle.equals(this.sidebarApi$title)) {
            this.sidebarApi$title = sidebarTitle;
            ScoreboardObjectiveUpdateS2CPacket packet = new ScoreboardObjectiveUpdateS2CPacket(SidebarAPIMod.SCOREBOARD_OBJECTIVE, 2);
            SOUS2CPacketAccessor accessor = (SOUS2CPacketAccessor) packet;
            accessor.setTitle(this.sidebarApi$title);
            this.sendPacket(packet);
        }

        int index = 0;

        for (SidebarLine line : this.sidebarApi$currentSidebar.getLinesFor(handler)) {
            if (this.sidebarApi$lines[index] == null || !this.sidebarApi$lines[index].equals(line, handler)) {

                this.sendPacket(new ScoreboardScoreUpdateS2CPacket(
                        SidebarAPIMod.FAKE_PLAYER_NAMES.get(index), SidebarAPIMod.OBJECTIVE_NAME, line.getValue(), Optional.of(line.getText(handler)), Optional.ofNullable(line.getNumberFormat(handler))));

                this.sidebarApi$lines[index] = line.immutableCopy(handler);
            }
            index++;
        }

        for (; index < this.sidebarApi$lines.length; index++) {
            if (this.sidebarApi$lines[index] != null) {
                this.sendPacket(new ScoreboardScoreResetS2CPacket(SidebarAPIMod.FAKE_PLAYER_NAMES.get(index), SidebarAPIMod.OBJECTIVE_NAME));
            }

            this.sidebarApi$lines[index] = null;
        }
    }

    @Override
    public void sidebarApi$updateState(boolean tick) {
        if (this.sidebarApi$currentSidebar == null) {
            this.sidebarApi$removeEmpty();
            return;
        }

        if (this.sidebarApi$alreadyHidden) {
            this.sidebarApi$setupInitial();
        } else if (tick && !this.sidebarApi$currentSidebar.manualTextUpdates()) {
            if (this.sidebarApi$currentTick % this.sidebarApi$currentSidebar.getUpdateRate() != 0) {
                this.sidebarApi$currentTick++;
                return;
            }

            this.sidebarApi$updateText();

            this.sidebarApi$currentTick++;
        }
    }

    @Override
    public void sidebarApi$add(SidebarInterface sidebar) {
        this.sidebarApi$sidebars.add(sidebar);
        this.sidebarApi$update(sidebar);
    }

    @Override
    public void sidebarApi$remove(SidebarInterface sidebar) {
        this.sidebarApi$sidebars.remove(sidebar);
        if (sidebar == this.sidebarApi$currentSidebar) {
            SidebarInterface newSidebar = null;
            for (var sidebar1 : this.sidebarApi$sidebars) {
                if (newSidebar == null || newSidebar.getPriority().isLowerThan(sidebar1.getPriority())) {
                    newSidebar = sidebar1;
                }
            }
            this.sidebarApi$currentSidebar = newSidebar;
        }
    }

    @Override
    public Set<SidebarInterface> sidebarApi$getAll() {
        return Collections.unmodifiableSet(this.sidebarApi$sidebars);
    }

    @Override
    public SidebarInterface sidebarApi$getCurrent() {
        return this.sidebarApi$currentSidebar;
    }

    @Override
    public void sidebarApi$update(SidebarInterface candidate) {
        if (this.sidebarApi$currentSidebar != null) {
            if (this.sidebarApi$currentSidebar.getPriority().isLowerThan(candidate.getPriority())) {
                this.sidebarApi$currentSidebar = candidate;
            }
        } else {
            this.sidebarApi$currentSidebar = candidate;
        }
    }

    @Override
    public void sidebarApi$clear() {
        this.sidebarApi$sidebars.clear();
        this.sidebarApi$currentSidebar = null;
    }
}
