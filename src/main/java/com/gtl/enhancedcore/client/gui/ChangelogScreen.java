package com.gtl.enhancedcore.client.gui;

import com.gtl.enhancedcore.common.config.ChangelogConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 更新公告弹窗（GTL-Enhancedcore，2026-09-02）。
 *
 * 观感贴近原版：半透明遮罩 + 与原版进度界面同色的描边面板 + 原版 Button，所有文字水平居中。
 * 内容与开关来自 {@link ChangelogConfig}（config/GTL-Enhancedcore/changelog.json）。
 *
 * <p>正文区支持滚动（2026-09-02 修复「公告展示不全」）：内容高于可视区时可用滚轮、
 * ↑↓、PageUp/PageDown、Home/End 滚动，右侧显示原版风格滚动条；用 GuiGraphics 的
 * scissor 裁剪保证文字不会溢出面板。
 *
 * 两个按钮：确定（仅关闭，下次启动仍弹）、不再提示（写入当前公告版本号，改版本号后会重新弹）。
 */
@OnlyIn(Dist.CLIENT)
public class ChangelogScreen extends Screen {

    /** 原版进度界面/tooltip 同款配色，避免自造风格。 */
    private static final int PANEL_BG = 0xF0100010;
    private static final int PANEL_BORDER_OUTER = 0x505000FF;
    private static final int PANEL_BORDER_INNER = 0x5028007F;

    private static final int TITLE_COLOR = 0xFFFFAA00;
    private static final int HEADING_COLOR = 0xFF55FFFF;
    private static final int BODY_COLOR = 0xFFDDDDDD;

    /** 滚动条：槽为半透明黑，滑块用与面板描边同系的淡紫。 */
    private static final int SCROLLBAR_TRACK = 0x40000000;
    private static final int SCROLLBAR_THUMB = 0xFF8080C0;

    private static final int PANEL_WIDTH = 360;
    private static final int PADDING = 14;
    private static final int LINE_HEIGHT = 11;
    /** 标题行额外占用的高度（放大 1.4 倍后需要更多行距）。 */
    private static final int TITLE_EXTRA_HEIGHT = 6;
    private static final int BUTTON_WIDTH = 110;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;
    /** 正文区与按钮之间的间隔。 */
    private static final int CONTENT_BUTTON_GAP = 8;
    private static final int SCROLLBAR_WIDTH = 4;
    /** 滚轮一格滚动的行数。 */
    private static final int SCROLL_LINES_PER_NOTCH = 3;

    private final List<Line> lines = new ArrayList<>();
    private final Screen parent;

    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    /** 正文可视区（不含边距与按钮区）。 */
    private int contentTop;
    private int contentHeight;
    /** 正文总高度（全部行叠加）。 */
    private int totalContentHeight;
    private int scrollOffset;

    private ChangelogScreen(Screen parent) {
        super(Component.translatable("gui.gtl_enhancedcore.changelog.title"));
        this.parent = parent;
    }

    /** 渲染用的一行：文本 + 颜色 + 是否为标题（放大绘制）。 */
    private record Line(Component text, int color, boolean title) {

        int height() {
            return LINE_HEIGHT + (this.title ? TITLE_EXTRA_HEIGHT : 0);
        }
    }

    /** 供事件类调用：满足弹出条件时打开公告界面。 */
    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new ChangelogScreen(parent));
    }

    @Override
    protected void init() {
        this.buildLines();

        this.totalContentHeight = 0;
        for (Line line : this.lines) {
            this.totalContentHeight += line.height();
        }

        // 面板高度：内容能装下就按内容算，否则占满屏幕可用高度（留 40px 边距）后靠滚动查看。
        int chromeHeight = PADDING * 2 + CONTENT_BUTTON_GAP + BUTTON_HEIGHT;
        int wanted = chromeHeight + this.totalContentHeight;
        int maxPanelHeight = Math.max(chromeHeight + LINE_HEIGHT * 4, this.height - 40);
        this.panelHeight = Math.min(wanted, maxPanelHeight);
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;

        this.contentTop = this.panelTop + PADDING;
        this.contentHeight = this.panelHeight - chromeHeight;
        this.scrollOffset = Math.min(this.scrollOffset, this.maxScroll());

        int buttonY = this.panelTop + this.panelHeight - PADDING - BUTTON_HEIGHT;
        int totalButtonWidth = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int buttonX = (this.width - totalButtonWidth) / 2;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.gtl_enhancedcore.changelog.confirm"),
                        button -> this.onClose())
                .bounds(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.gtl_enhancedcore.changelog.never_again"),
                        button -> {
                            ChangelogConfig.markSeen();
                            this.onClose();
                        })
                .bounds(buttonX + BUTTON_WIDTH + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    /** 可滚动的最大距离；0 表示内容完全装得下。 */
    private int maxScroll() {
        return Math.max(0, this.totalContentHeight - this.contentHeight);
    }

    /** 把配置内容折成可渲染的行列表；正文按可用宽度自动换行。 */
    private void buildLines() {
        this.lines.clear();
        ChangelogConfig config = ChangelogConfig.get();
        int textWidth = PANEL_WIDTH - PADDING * 2 - SCROLLBAR_WIDTH - 2;

        if (!config.getTitle().isBlank()) {
            this.lines.add(new Line(Component.literal(config.getTitle()), TITLE_COLOR, true));
            this.lines.add(new Line(Component.empty(), BODY_COLOR, false));
        }

        List<ChangelogConfig.Section> sections = config.getSections();
        for (int i = 0; i < sections.size(); i++) {
            ChangelogConfig.Section section = sections.get(i);
            if (!section.heading().isBlank()) {
                this.lines.add(new Line(Component.literal(section.heading()), HEADING_COLOR, false));
            }
            for (String raw : section.lines()) {
                if (raw.isBlank()) {
                    this.lines.add(new Line(Component.empty(), BODY_COLOR, false));
                    continue;
                }
                if (this.font.width(raw) <= textWidth) {
                    this.lines.add(new Line(Component.literal(raw), BODY_COLOR, false));
                    continue;
                }
                // 超宽条目按像素宽度折行，每段单独成行以便居中绘制。
                for (FormattedCharSequence part : this.font.split(Component.literal(raw), textWidth)) {
                    StringBuilder builder = new StringBuilder();
                    part.accept((index, style, codePoint) -> {
                        builder.appendCodePoint(codePoint);
                        return true;
                    });
                    this.lines.add(new Line(Component.literal(builder.toString()), BODY_COLOR, false));
                }
            }
            if (i < sections.size() - 1) {
                this.lines.add(new Line(Component.empty(), BODY_COLOR, false));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.20.1 的 Screen.renderBackground 只收 GuiGraphics（SRG m_280273_）；四参重载是 1.20.2+ 才有的。
        this.renderBackground(graphics);

        int right = this.panelLeft + PANEL_WIDTH;
        int bottom = this.panelTop + this.panelHeight;

        graphics.fill(this.panelLeft, this.panelTop, right, bottom, PANEL_BG);
        graphics.fill(this.panelLeft, this.panelTop - 1, right, this.panelTop, PANEL_BORDER_OUTER);
        graphics.fill(this.panelLeft, bottom, right, bottom + 1, PANEL_BORDER_OUTER);
        graphics.fill(this.panelLeft - 1, this.panelTop, this.panelLeft, bottom, PANEL_BORDER_OUTER);
        graphics.fill(right, this.panelTop, right + 1, bottom, PANEL_BORDER_OUTER);
        graphics.fill(this.panelLeft + 1, this.panelTop + 1, right - 1, this.panelTop + 2, PANEL_BORDER_INNER);
        graphics.fill(this.panelLeft + 1, bottom - 2, right - 1, bottom - 1, PANEL_BORDER_INNER);

        this.renderContent(graphics);
        this.renderScrollbar(graphics, right);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 正文区：scissor 裁剪 + 按滚动偏移绘制，全部行水平居中。 */
    private void renderContent(GuiGraphics graphics) {
        int contentBottom = this.contentTop + this.contentHeight;
        graphics.enableScissor(this.panelLeft + 1, this.contentTop, this.panelLeft + PANEL_WIDTH - 1, contentBottom);

        int centerX = this.panelLeft + (PANEL_WIDTH - SCROLLBAR_WIDTH - 2) / 2;
        int y = this.contentTop - this.scrollOffset;
        for (Line line : this.lines) {
            int lineHeight = line.height();
            // 完全在可视区之外的行直接跳过绘制，只推进 y。
            if (y + lineHeight >= this.contentTop && y <= contentBottom) {
                if (!line.text().getString().isEmpty()) {
                    if (line.title()) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(centerX, y, 0.0D);
                        graphics.pose().scale(1.4F, 1.4F, 1.0F);
                        graphics.drawString(this.font, line.text(),
                                -this.font.width(line.text()) / 2, 0, line.color(), true);
                        graphics.pose().popPose();
                    } else {
                        graphics.drawCenteredString(this.font, line.text(), centerX, y, line.color());
                    }
                }
            }
            y += lineHeight;
        }

        graphics.disableScissor();
    }

    /** 需要滚动时在正文区右侧画原版风格滚动条。 */
    private void renderScrollbar(GuiGraphics graphics, int panelRight) {
        int maxScroll = this.maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        int trackLeft = panelRight - PADDING / 2 - SCROLLBAR_WIDTH;
        int trackRight = trackLeft + SCROLLBAR_WIDTH;
        int trackTop = this.contentTop;
        int trackBottom = this.contentTop + this.contentHeight;
        graphics.fill(trackLeft, trackTop, trackRight, trackBottom, SCROLLBAR_TRACK);

        int thumbHeight = Math.max(16,
                this.contentHeight * this.contentHeight / Math.max(1, this.totalContentHeight));
        int travel = this.contentHeight - thumbHeight;
        int thumbTop = trackTop + (travel * this.scrollOffset / maxScroll);
        graphics.fill(trackLeft, thumbTop, trackRight, thumbTop + thumbHeight, SCROLLBAR_THUMB);
    }

    private void scrollBy(int delta) {
        int maxScroll = this.maxScroll();
        if (maxScroll <= 0) {
            return;
        }
        this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset + delta));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.maxScroll() > 0) {
            this.scrollBy((int) (-delta * LINE_HEIGHT * SCROLL_LINES_PER_NOTCH));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.maxScroll() > 0) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_DOWN -> {
                    this.scrollBy(LINE_HEIGHT);
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    this.scrollBy(-LINE_HEIGHT);
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_DOWN -> {
                    this.scrollBy(this.contentHeight);
                    return true;
                }
                case GLFW.GLFW_KEY_PAGE_UP -> {
                    this.scrollBy(-this.contentHeight);
                    return true;
                }
                case GLFW.GLFW_KEY_HOME -> {
                    this.scrollOffset = 0;
                    return true;
                }
                case GLFW.GLFW_KEY_END -> {
                    this.scrollOffset = this.maxScroll();
                    return true;
                }
                default -> {
                    // 其余按键交给父类（ESC 关闭等）。
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    /** 公告是提示性界面，允许 ESC 关闭（等同"确定"）。 */
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    /** 主菜单弹窗不应暂停世界（此时无世界，仅为语义正确）。 */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
