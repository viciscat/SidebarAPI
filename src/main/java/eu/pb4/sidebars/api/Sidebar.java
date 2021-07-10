package eu.pb4.sidebars.api;

import eu.pb4.sidebars.api.lines.LineBuilder;
import eu.pb4.sidebars.api.lines.SidebarLine;
import eu.pb4.sidebars.api.lines.SimpleSidebarLine;
import eu.pb4.sidebars.interfaces.SidebarHolder;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.Consumer;

/**
 * Basic sidebar with all of basic functionality
 */
public class Sidebar {
    protected List<SidebarLine> elements = new ArrayList<>();
    protected Set<ServerPlayNetworkHandler> players = new HashSet<>();
    protected Priority priority;
    protected Text title;
    protected boolean isDirty = false;

    protected boolean isActive = false;

    public Sidebar(Priority priority) {
        this.priority = priority;
        this.title = new LiteralText("");
    }

    public Sidebar(Text title, Priority priority) {
        this.priority = priority;
        this.title = title;
    }

    public Priority getPriority() {
        return this.priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
        if (this.isActive) {
            for (ServerPlayNetworkHandler player : this.players) {
                ((SidebarHolder) player).updateCurrentSidebar(this);
            }
        }
    }

    public Text getTitle() {
        return this.title;
    }

    public void setTitle(Text title) {
        this.title = title;
    }

    public void setLine(int value, Text text) {
        for (SidebarLine line : this.elements) {
            if (line.getValue() == value) {
                this.elements.set(this.elements.indexOf(line), new SimpleSidebarLine(value, text, this));
                return;
            }
        }

        this.elements.add(new SimpleSidebarLine(value, text, this));
        this.isDirty = true;
    }

    public void setLine(SidebarLine line) {
        for (SidebarLine cLine : this.elements) {
            if (line.getValue() == cLine.getValue()) {
                line.setSidebar(this);
                this.elements.set(this.elements.indexOf(cLine), line);
                return;
            }
        }

        this.elements.add(line);
        this.isDirty = true;
    }

    public void addLines(SidebarLine... lines) {
        for (SidebarLine line : lines) {
            line.setSidebar(this);
            this.elements.add(line);
        }

        this.isDirty = true;
    }


    public void addLines(Text... texts) {
        if (this.elements.isEmpty()) {
            int lastLine = texts.length;
            for (Text t : texts) {
                this.elements.add(new SimpleSidebarLine(--lastLine, t, this));
            }
        } else {
            this.sortIfDirty();
            int lastLine = this.elements.get(this.elements.size()).getValue();
            for (Text t : texts) {
                this.elements.add(new SimpleSidebarLine(--lastLine, t, this));
            }
        }
    }

    public void removeLine(SidebarLine line) {
        this.elements.remove(line);
        line.setSidebar(null);
    }

    public void removeLine(int value) {
        for (SidebarLine line : new ArrayList<>(this.elements)) {
            if (line.getValue() == value) {
                this.elements.remove(line);
                line.setSidebar(null);
            }
        }
    }

    public SidebarLine getLine(int value) {
        for (SidebarLine line : this.elements) {
            if (line.getValue() == value) {
                return line;
            }
        }

        return null;
    }

    public void replaceLines(Text... texts) {
        this.clearLines();
        this.addLines(texts);
    }

    public void replaceLines(SidebarLine... lines) {
        this.clearLines();
        this.addLines(lines);
    }

    public void replaceLines(LineBuilder builder) {
        this.replaceLines(builder.getLines().toArray(new SidebarLine[0]));
    }

    public void clearLines() {
        for (SidebarLine line : this.elements) {
            line.setSidebar(null);
        }

        this.elements.clear();
    }

    public void set(Consumer<LineBuilder> consumer) {
        LineBuilder builder = new LineBuilder();
        consumer.accept(builder);
        this.replaceLines(builder);
    }

    public boolean isDirty() {
        return this.isDirty;
    }

    public void markDirty() {
        this.isDirty = true;
    }

    public List<SidebarLine> getLinesFor(ServerPlayNetworkHandler handler) {
        this.sortIfDirty();

        return this.elements.subList(0, Math.min(14, this.elements.size()));
    }

    protected void sortIfDirty() {
        if (this.isDirty) {
            this.isDirty = false;
            Collections.sort(this.elements, (a, b) ->
                    a.getValue() < b.getValue()
                            ? 1
                            : a.getValue() > b.getValue()
                            ? -1 : 0);
        }
    }

    public void show() {
        if (!isActive) {
            this.isActive = true;
            for (ServerPlayNetworkHandler player : this.players) {
                ((SidebarHolder) player).addSidebar(this);
            }
        }
    }

    public void hide() {
        if (this.isActive) {
            this.isActive = false;
            for (ServerPlayNetworkHandler player : this.players) {
                ((SidebarHolder) player).removeSidebar(this);
            }
        }
    }

    public boolean isActive() {
        return this.isActive;
    }

    public void addPlayer(ServerPlayNetworkHandler handler) {
        if (this.players.add(handler)) {
            if (isActive) {
                ((SidebarHolder) handler).addSidebar(this);
            }
        }
    }

    public void removePlayer(ServerPlayNetworkHandler handler) {
        if (this.players.remove(handler)) {
            if (isActive) {
                if (!handler.player.isDisconnected()) {
                    ((SidebarHolder) handler).removeSidebar(this);
                }
            }
        }
    }

    public void addPlayer(ServerPlayerEntity player) {
        this.addPlayer(player.networkHandler);
    }

    public void removePlayer(ServerPlayerEntity player) {
        this.removePlayer(player.networkHandler);
    }

    public Set<ServerPlayNetworkHandler> getPlayerHandlerSet() {
        return Collections.unmodifiableSet(this.players);
    }


    public enum Priority {
        LOWEST(0),
        LOW(1),
        MEDIUM(2),
        HIGH(3),
        OVERRIDE(4);

        private final int value;

        Priority(int value) {
            this.value = value;
        }

        public boolean isLowerThan(Priority priority) {
            return this.value <= priority.value;
        }
    }
}
