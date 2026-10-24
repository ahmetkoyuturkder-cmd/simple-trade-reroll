package dev.furkan.simpletradereroll;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import dev.furkan.simpletradereroll.mixin.ContainerScreenPositionAccessor;

/** All controls stay on the vanilla merchant screen, so its container remains open. */
public final class SimpleTradeRerollClient implements ClientModInitializer {
    private static SimpleTradeReroll.Target selected;
    private static int selectedMaxLevel = 1;
    private static MenuUi activeUi;

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof MerchantScreen) {
                MenuUi ui = new MenuUi(client, screen);
                activeUi = ui;
                ScreenEvents.afterTick(screen).register(s -> ui.updateProgress());
            }
        });
    }

    /** Called before vanilla container shortcuts such as inventory and hotbar keys. */
    public static boolean handleFocusedTextKey(Screen screen, KeyEvent event) {
        return activeUi != null && activeUi.screen == screen && activeUi.handleFocusedTextKey(event);
    }

    public static boolean handleCompactPanelClick(Screen screen, MouseButtonEvent event) {
        return activeUi != null && activeUi.screen == screen && activeUi.handleCompactPanelClick(event);
    }

    private static void onServer(Minecraft client, Consumer<ServerPlayer> action) {
        var server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            if (client.player != null) client.player.sendSystemMessage(
                    Component.literal("[Trade Reroll] ").append(
                            Component.translatable("message.simple_trade_reroll.singleplayer_only")));
            return;
        }
        var playerId = client.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) action.accept(player);
        });
    }

    private enum Mode { BOOK, RESULT, PAYMENT }
    private record Entry(SimpleTradeReroll.Target target, String label, int maxLevel) {}

    private static final class MenuUi {
        private static final int PAGE_SIZE = 2;
        private static final int PANEL_WIDTH = 96;
        private final Minecraft client;
        private final Screen screen;
        private final int x;
        private final int top;
        private final boolean compact;
        private final List<AbstractWidget> listPanel = new ArrayList<>();
        private final List<AbstractWidget> configPanel = new ArrayList<>();
        private final List<Button> choices = new ArrayList<>();
        private final EditBox search;
        private final EditBox levelInput;
        private final EditBox emeraldInput;
        private final Button selectedLabel;
        private final Button levelLabel;
        private final Button emeraldLabel;
        private final Button pageLabel;
        private final Button targetButton;
        private final Button refreshButton;
        private final Button autoButton;
        private final Button compactToggle;
        private final PanelBackdrop backdrop;
        private final Button progressLabel;
        private List<Entry> entries = List.of();
        private Mode mode = Mode.BOOK;
        private int page = 0;
        private boolean open = false;
        private boolean configuring = false;

        private MenuUi(Minecraft client, Screen screen) {
            this.client = client;
            this.screen = screen;
            // Recipe viewers can shift the merchant screen. Place the controls in
            // the empty strip to its left, above the recipe viewer's left panel.
            var position = (ContainerScreenPositionAccessor) screen;
            int left = position.simpleTradeReroll$getLeftPos();
            int right = left + position.simpleTradeReroll$getImageWidth();
            int leftSpace = left;
            int rightSpace = screen.width - right;
            this.compact = leftSpace < PANEL_WIDTH + 4 && rightSpace < PANEL_WIDTH + 4;
            this.x = leftSpace >= PANEL_WIDTH + 4 ? left - PANEL_WIDTH - 2
                    : rightSpace >= PANEL_WIDTH + 4 ? right + 2
                    : Math.max(2, (screen.width - PANEL_WIDTH) / 2);
            this.top = compact ? Math.max(2, (screen.height - 125) / 2)
                    : position.simpleTradeReroll$getTopPos();

            if (compact) {
                backdrop = new PanelBackdrop(x - 2, top - 2, PANEL_WIDTH + 4, 127);
                backdrop.visible = false;
                add(backdrop);
                compactToggle = Button.builder(Component.literal("TR"), b -> {
                    if (open) setOpen(false);
                    else {
                        configuring = selected != null;
                        setOpen(true);
                    }
                }).bounds(Math.max(2, left - 25), position.simpleTradeReroll$getTopPos() + 2, 23, 18).build();
                compactToggle.setTooltip(Tooltip.create(Component.translatable("ui.simple_trade_reroll.compact_help")));
                add(compactToggle);
            } else {
                backdrop = null;
                compactToggle = null;
            }

            refreshButton = Button.builder(Component.translatable("ui.simple_trade_reroll.refresh"), b ->
                    onServer(client, SimpleTradeReroll::manual)).bounds(x, top + 2, 35, 18).build();
            add(refreshButton);
            targetButton = Button.builder(Component.translatable(selected == null
                            ? "ui.simple_trade_reroll.target" : "ui.simple_trade_reroll.selected_short"),
                    b -> {
                        if (open) setOpen(false);
                        else {
                            configuring = selected != null;
                            setOpen(true);
                        }
                    }).bounds(x + 36, top + 2, 35, 18).build();
            if (selected != null) targetButton.setTooltip(Tooltip.create(selected.summary()));
            add(targetButton);
            autoButton = Button.builder(Component.translatable("ui.simple_trade_reroll.auto"), b -> {
                if (client.player == null) return;
                if (SimpleTradeReroll.isRunning(client.player.getUUID())) {
                    onServer(client, SimpleTradeReroll::stopAuto);
                } else {
                    if (open && configuring) startConfigured();
                    else onServer(client, p -> SimpleTradeReroll.startAuto(p, selected));
                }
            }).bounds(x + 72, top + 2, 24, 18).build();
            autoButton.setTooltip(Tooltip.create(Component.translatable("ui.simple_trade_reroll.auto_help")));
            add(autoButton);

            addList(Button.builder(Component.translatable("ui.simple_trade_reroll.book"), b -> setMode(Mode.BOOK))
                    .bounds(x, top + 22, 31, 16).build());
            addList(Button.builder(Component.translatable("ui.simple_trade_reroll.sold"), b -> setMode(Mode.RESULT))
                    .bounds(x + 32, top + 22, 31, 16).build());
            addList(Button.builder(Component.translatable("ui.simple_trade_reroll.bought"), b -> setMode(Mode.PAYMENT))
                    .bounds(x + 64, top + 22, 32, 16).build());

            search = new EditBox(Screens.getFont(screen), x, top + 40, PANEL_WIDTH, 17,
                    Component.translatable("ui.simple_trade_reroll.search"));
            search.setHint(Component.translatable("ui.simple_trade_reroll.search_hint"));
            search.setMaxLength(60);
            search.setResponder(value -> { page = 0; refresh(); });
            addList(search);

            for (int i = 0; i < PAGE_SIZE; i++) {
                final int slot = i;
                Button choice = Button.builder(Component.literal(""), b -> choose(slot))
                        .bounds(x, top + 59 + i * 17, PANEL_WIDTH, 16).build();
                choices.add(choice);
                addList(choice);
            }
            addList(Button.builder(Component.literal("◀"), b -> {
                if (page > 0) { page--; refresh(); }
            }).bounds(x, top + 96, 20, 16).build());
            pageLabel = Button.builder(Component.literal("1/1"), b -> {})
                    .bounds(x + 21, top + 96, 54, 16).build();
            pageLabel.active = false;
            addList(pageLabel);
            addList(Button.builder(Component.literal("▶"), b -> {
                if ((page + 1) * PAGE_SIZE < entries.size()) { page++; refresh(); }
            }).bounds(x + 76, top + 96, 20, 16).build());

            selectedLabel = Button.builder(selected == null
                            ? Component.translatable("ui.simple_trade_reroll.target") : Component.literal(selected.label()), b -> {})
                    .bounds(x, top + 22, PANEL_WIDTH, 16).build();
            selectedLabel.active = false;
            if (selected != null) selectedLabel.setTooltip(Tooltip.create(Component.literal(selected.label())));
            addConfig(selectedLabel);

            levelLabel = Button.builder(Component.translatable("ui.simple_trade_reroll.level_short"), b -> {})
                    .bounds(x, top + 41, 25, 16).build();
            levelLabel.active = false;
            levelLabel.setTooltip(Tooltip.create(Component.translatable("ui.simple_trade_reroll.level_help")));
            addConfig(levelLabel);
            levelInput = new EditBox(Screens.getFont(screen), x + 27, top + 41, 69, 16,
                    Component.translatable("ui.simple_trade_reroll.level"));
            levelInput.setMaxLength(3);
            levelInput.setValue(selected == null ? "0" : Integer.toString(selected.level()));
            levelInput.setTooltip(Tooltip.create(Component.translatable("ui.simple_trade_reroll.level_help")));
            addConfig(levelInput);

            emeraldLabel = Button.builder(Component.translatable("ui.simple_trade_reroll.emerald_short"), b -> {})
                    .bounds(x, top + 60, 25, 16).build();
            emeraldLabel.active = false;
            emeraldLabel.setTooltip(Tooltip.create(Component.translatable("ui.simple_trade_reroll.emerald_help")));
            addConfig(emeraldLabel);
            emeraldInput = new EditBox(Screens.getFont(screen), x + 27, top + 60, 69, 16,
                    Component.translatable("ui.simple_trade_reroll.emerald"));
            emeraldInput.setMaxLength(2);
            emeraldInput.setValue(selected == null ? "0" : Integer.toString(selected.maxEmeraldCost()));
            emeraldInput.setTooltip(Tooltip.create(Component.translatable("ui.simple_trade_reroll.emerald_help")));
            addConfig(emeraldInput);

            addConfig(Button.builder(Component.translatable("ui.simple_trade_reroll.start"), b -> startConfigured())
                    .bounds(x, top + 80, 47, 18).build());
            addConfig(Button.builder(Component.translatable("ui.simple_trade_reroll.list"), b -> {
                configuring = false;
                setOpen(true);
            }).bounds(x + 49, top + 80, 47, 18).build());

            progressLabel = Button.builder(Component.empty(), b -> {})
                    .bounds(x, top + 113, PANEL_WIDTH, 10).build();
            progressLabel.active = false;
            progressLabel.visible = false;
            add(progressLabel);

            setOpen(false);
            setMode(Mode.BOOK);
        }

        private void add(AbstractWidget widget) {
            Screens.getWidgets(screen).add(widget);
        }

        private void addList(AbstractWidget widget) {
            listPanel.add(widget);
            add(widget);
        }

        private void addConfig(AbstractWidget widget) {
            configPanel.add(widget);
            add(widget);
        }

        private void setOpen(boolean value) {
            open = value;
            if (compact) {
                backdrop.visible = value;
                refreshButton.visible = value;
                targetButton.visible = value;
                autoButton.visible = value;
            }
            for (AbstractWidget widget : listPanel) widget.visible = value && !configuring;
            for (AbstractWidget widget : configPanel) widget.visible = value && configuring;
            if (value && configuring) {
                boolean book = selected != null && selected.kind() == SimpleTradeReroll.TargetKind.ENCHANTMENT;
                boolean hasEmeraldPrice = selected != null
                        && selected.kind() != SimpleTradeReroll.TargetKind.PAYMENT_ITEM;
                levelInput.active = book;
                emeraldInput.active = hasEmeraldPrice;
                levelLabel.setTooltip(Tooltip.create(book
                        ? Component.translatable("ui.simple_trade_reroll.level_range", selectedMaxLevel)
                        : Component.translatable("ui.simple_trade_reroll.level_book_only")));
                emeraldLabel.setTooltip(Tooltip.create(Component.translatable(hasEmeraldPrice
                        ? "ui.simple_trade_reroll.emerald_help"
                        : "ui.simple_trade_reroll.emerald_not_applicable")));
            }
            if (value && !configuring) refresh();
        }

        private void setMode(Mode value) {
            mode = value;
            page = 0;
            search.setValue("");
            refresh();
        }

        private void refresh() {
            if (client.level == null) return;
            String filter = search.getValue().toLowerCase(Locale.ROOT).trim();
            List<Entry> found = new ArrayList<>();
            if (mode == Mode.BOOK) {
                client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                        .filter(holder -> holder.is(EnchantmentTags.TRADEABLE))
                        .forEach(holder -> {
                            String id = holder.key().identifier().toString();
                            String label = holder.value().description().getString();
                            if (matchesFilter(label, id, filter)) found.add(new Entry(
                                    new SimpleTradeReroll.Target(SimpleTradeReroll.TargetKind.ENCHANTMENT,
                                            id, label, 0, 0), label, holder.value().getMaxLevel()));
                        });
            } else {
                for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
                    Item item = BuiltInRegistries.ITEM.getValue(id);
                    String label = item.getName(new ItemStack(item)).getString();
                    if (!matchesFilter(label, id.toString(), filter)) continue;
                    var kind = mode == Mode.RESULT ? SimpleTradeReroll.TargetKind.RESULT_ITEM
                            : SimpleTradeReroll.TargetKind.PAYMENT_ITEM;
                    found.add(new Entry(new SimpleTradeReroll.Target(kind, id.toString(), label, 0, 0), label, 0));
                }
            }
            found.sort(Comparator.comparing(Entry::label, String.CASE_INSENSITIVE_ORDER));
            entries = found;
            int maxPage = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
            page = Math.min(page, maxPage);
            pageLabel.setMessage(Component.literal((page + 1) + "/" + (maxPage + 1)));
            for (int i = 0; i < choices.size(); i++) {
                int index = page * PAGE_SIZE + i;
                Button button = choices.get(i);
                button.visible = open && !configuring && index < entries.size();
                if (index < entries.size()) {
                    button.setMessage(Component.literal(entries.get(index).label()));
                    button.setTooltip(Tooltip.create(Component.literal(entries.get(index).label())));
                }
            }
        }

        private static boolean matchesFilter(String label, String id, String filter) {
            return filter.isEmpty() || label.toLowerCase(Locale.ROOT).contains(filter)
                    || id.toLowerCase(Locale.ROOT).contains(filter);
        }

        private void choose(int slot) {
            int index = page * PAGE_SIZE + slot;
            if (index >= entries.size()) return;
            Entry entry = entries.get(index);
            selected = entry.target();
            selectedMaxLevel = entry.maxLevel();
            targetButton.setMessage(Component.translatable("ui.simple_trade_reroll.selected_short"));
            targetButton.setTooltip(Tooltip.create(selected.summary()));
            selectedLabel.setMessage(Component.literal(selected.label()));
            selectedLabel.setTooltip(Tooltip.create(Component.literal(selected.label())));
            levelInput.setValue("0");
            emeraldInput.setValue("0");
            configuring = true;
            setOpen(true);
        }

        private void startConfigured() {
            if (selected == null) return;
            int level;
            int emeralds;
            try {
                level = Integer.parseInt(levelInput.getValue());
                emeralds = Integer.parseInt(emeraldInput.getValue());
            } catch (NumberFormatException ex) {
                tell("message.simple_trade_reroll.enter_numbers");
                return;
            }
            if (selected.kind() != SimpleTradeReroll.TargetKind.ENCHANTMENT) level = 0;
            if (selected.kind() == SimpleTradeReroll.TargetKind.PAYMENT_ITEM) emeralds = 0;
            if (level < 0 || (selected.kind() == SimpleTradeReroll.TargetKind.ENCHANTMENT
                    && level > selectedMaxLevel)) {
                tell("message.simple_trade_reroll.invalid_level", selectedMaxLevel);
                return;
            }
            if (emeralds < 0 || emeralds > 64) {
                tell("message.simple_trade_reroll.invalid_emerald");
                return;
            }
            selected = new SimpleTradeReroll.Target(selected.kind(), selected.id(), selected.label(),
                    level, emeralds);
            targetButton.setTooltip(Tooltip.create(selected.summary()));
            SimpleTradeReroll.Target chosen = selected;
            setOpen(false);
            onServer(client, player -> SimpleTradeReroll.startAuto(player, chosen));
        }

        private boolean handleFocusedTextKey(KeyEvent event) {
            if (compact && open && event.isEscape()) {
                setOpen(false);
                return true;
            }
            for (EditBox input : List.of(search, levelInput, emeraldInput)) {
                if (input.visible && input.active && input.isFocused()) {
                    if (event.isEscape()) return false;
                    if (input.keyPressed(event)) return true;
                    var options = client.options;
                    if (options.keyInventory.matches(event) || options.keySwapOffhand.matches(event)
                            || options.keyPickItem.matches(event) || options.keyDrop.matches(event)) return true;
                    for (var hotbarKey : options.keyHotbarSlots) {
                        if (hotbarKey.matches(event)) return true;
                    }
                    return false;
                }
            }
            return false;
        }

        private boolean handleCompactPanelClick(MouseButtonEvent event) {
            if (!compact || !open || !backdrop.isMouseOver(event.x(), event.y())) return false;
            for (AbstractWidget widget : listPanel) {
                if (widget.visible && widget.active && widget.isMouseOver(event.x(), event.y())) return false;
            }
            for (AbstractWidget widget : configPanel) {
                if (widget.visible && widget.active && widget.isMouseOver(event.x(), event.y())) return false;
            }
            return !refreshButton.isMouseOver(event.x(), event.y())
                    && !targetButton.isMouseOver(event.x(), event.y())
                    && !autoButton.isMouseOver(event.x(), event.y());
        }

        private void updateProgress() {
            int attempts = client.player == null ? -1 : SimpleTradeReroll.attempts(client.player.getUUID());
            progressLabel.visible = attempts >= 0 && (!compact || open);
            if (attempts >= 0) {
                progressLabel.setMessage(Component.translatable("ui.simple_trade_reroll.progress_short",
                        attempts, SimpleTradeReroll.MAX_ATTEMPTS));
                progressLabel.setTooltip(Tooltip.create(Component.translatable("ui.simple_trade_reroll.progress_help",
                        attempts, SimpleTradeReroll.MAX_ATTEMPTS)));
            }
            if (compact) {
                compactToggle.setMessage(Component.literal(attempts >= 0 ? Integer.toString(attempts) : "TR"));
                compactToggle.setTooltip(Tooltip.create(attempts >= 0
                        ? Component.translatable("ui.simple_trade_reroll.progress_help", attempts,
                                SimpleTradeReroll.MAX_ATTEMPTS)
                        : Component.translatable("ui.simple_trade_reroll.compact_help")));
            }
        }

        private void tell(String key, Object... args) {
            if (client.player != null) client.player.sendSystemMessage(
                    Component.literal("[Trade Reroll] ").append(Component.translatable(key, args)));
        }

        private static final class PanelBackdrop extends AbstractWidget {
            private PanelBackdrop(int x, int y, int width, int height) {
                super(x, y, width, height, Component.empty());
                active = false;
            }

            @Override
            protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                    float partialTick) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), 0xF0C6C6C6);
                graphics.fill(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2, 0xF0333333);
            }

            @Override
            protected void updateWidgetNarration(NarrationElementOutput output) {}
        }
    }
}
