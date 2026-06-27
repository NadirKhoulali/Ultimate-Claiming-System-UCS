package com.nadirkhoulali.ucs.client;

import com.nadirkhoulali.ucs.core.model.MapTileKey;
import com.nadirkhoulali.ucs.map.TerrainTilePayload;
import com.nadirkhoulali.ucs.map.TerrainTileResponseStatus;
import com.nadirkhoulali.ucs.map.TerrainTileStreamResponse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;

public final class UcsTerrainMapScreen extends Screen {
    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 18;
    private static final int MAP_MARGIN = 10;
    private static final int MAX_REQUEST_TILES = 256;
    private static final int PAN_KEY_PIXELS = 96;
    private static final List<String> CYCLE_DIMENSIONS = List.of(
            "minecraft:overworld",
            "minecraft:the_nether",
            "minecraft:the_end"
    );

    private String dimension;
    private double centerBlockX;
    private double centerBlockZ;
    private int zoom;
    private int activeRequestId = -1;
    private List<MapTileKey> requestedTiles = List.of();

    public UcsTerrainMapScreen(String dimension, int centerBlockX, int centerBlockZ, int zoom) {
        super(Component.translatable("screen.ucs.map.title"));
        this.dimension = dimension;
        this.centerBlockX = centerBlockX;
        this.centerBlockZ = centerBlockZ;
        this.zoom = UcsTerrainMapViewport.clampZoom(zoom);
    }

    @Override
    public void tick() {
        requestVisibleTiles();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MapBounds bounds = mapBounds();
        graphics.fill(0, 0, width, height, 0xFF0D1117);
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFF151A21);
        renderTiles(graphics, bounds);
        renderPlayerMarker(graphics, bounds);
        renderFrame(graphics, bounds);
        renderHeader(graphics, bounds);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && insideMap(mouseX, mouseY)) {
            int scale = UcsTerrainMapViewport.blockScale(zoom);
            centerBlockX -= dragX * scale;
            centerBlockZ -= dragY * scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!insideMap(mouseX, mouseY) || scrollY == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int nextZoom = UcsTerrainMapViewport.clampZoom(zoom + (scrollY < 0.0D ? 1 : -1));
        if (nextZoom == zoom) {
            return true;
        }

        MapBounds bounds = mapBounds();
        double blockXUnderCursor = screenToBlockX(mouseX, bounds);
        double blockZUnderCursor = screenToBlockZ(mouseY, bounds);
        zoom = nextZoom;
        int scale = UcsTerrainMapViewport.blockScale(zoom);
        centerBlockX = blockXUnderCursor - (mouseX - bounds.centerX()) * scale;
        centerBlockZ = blockZUnderCursor - (mouseY - bounds.centerY()) * scale;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int scale = UcsTerrainMapViewport.blockScale(zoom);
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
            centerBlockX -= (double) PAN_KEY_PIXELS * scale;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
            centerBlockX += (double) PAN_KEY_PIXELS * scale;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
            centerBlockZ -= (double) PAN_KEY_PIXELS * scale;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
            centerBlockZ += (double) PAN_KEY_PIXELS * scale;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            zoom = UcsTerrainMapViewport.clampZoom(zoom - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoom = UcsTerrainMapViewport.clampZoom(zoom + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            cycleDimension(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            cycleDimension(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            recenterOnPlayer();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        cancelActiveRequest();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void requestVisibleTiles() {
        MapBounds bounds = mapBounds();
        List<MapTileKey> visibleTiles = visibleTiles(bounds);
        if (visibleTiles.equals(requestedTiles)) {
            return;
        }
        cancelActiveRequest();
        activeRequestId = UcsTerrainMapClient.nextRequestId();
        requestedTiles = visibleTiles;
        if (!UcsTerrainMapClient.requestTiles(activeRequestId, visibleTiles)) {
            activeRequestId = -1;
        }
    }

    private void cancelActiveRequest() {
        if (activeRequestId >= 0) {
            UcsTerrainMapClient.cancelRequest(activeRequestId);
            activeRequestId = -1;
        }
    }

    private void renderTiles(GuiGraphics graphics, MapBounds bounds) {
        List<MapTileKey> visibleTiles = visibleTiles(bounds);
        for (MapTileKey key : visibleTiles) {
            UcsTerrainMapViewport.TileBounds tileBounds = UcsTerrainMapViewport.tileBounds(
                    key,
                    centerBlockX,
                    centerBlockZ,
                    bounds.left(),
                    bounds.top(),
                    bounds.width(),
                    bounds.height()
            );
            renderTile(graphics, bounds, key, tileBounds);
        }
    }

    private void renderTile(GuiGraphics graphics, MapBounds mapBounds, MapTileKey key, UcsTerrainMapViewport.TileBounds tileBounds) {
        Optional<TerrainTileStreamResponse> response = UcsTerrainTileClientCache.latest(key);
        if (response.isEmpty()) {
            drawStatusTile(graphics, mapBounds, tileBounds, 0xFF202732, 0xFF2E3744);
            return;
        }

        TerrainTileStreamResponse tile = response.orElseThrow();
        if ((tile.status() == TerrainTileResponseStatus.HIT || tile.status() == TerrainTileResponseStatus.GENERATED) && tile.payload().length > 0) {
            try {
                TerrainTilePayload payload = TerrainTilePayload.decode(tile.payload());
                if (payload.key().equals(key)) {
                    drawTerrainPayload(graphics, mapBounds, tileBounds, payload);
                    drawTileBorder(graphics, mapBounds, tileBounds, 0x663C4652);
                    return;
                }
            } catch (RuntimeException ignored) {
                drawStatusTile(graphics, mapBounds, tileBounds, 0xFF4A2530, 0xFF7B3945);
                return;
            }
        }

        switch (tile.status()) {
            case PLACEHOLDER -> drawStatusTile(graphics, mapBounds, tileBounds, 0xFF2B2F36, 0xFF3A404A);
            case RATE_LIMITED -> drawStatusTile(graphics, mapBounds, tileBounds, 0xFF3B3320, 0xFF6D5723);
            case ERROR -> drawStatusTile(graphics, mapBounds, tileBounds, 0xFF40252B, 0xFF7A3942);
            case CANCELLED -> drawStatusTile(graphics, mapBounds, tileBounds, 0xFF24282F, 0xFF343A44);
            default -> drawStatusTile(graphics, mapBounds, tileBounds, 0xFF202732, 0xFF2E3744);
        }
    }

    private void drawTerrainPayload(GuiGraphics graphics, MapBounds mapBounds, UcsTerrainMapViewport.TileBounds tileBounds, TerrainTilePayload payload) {
        int sampleStep = Math.max(1, payload.size() / 32);
        double cellScale = tileBounds.width() / (double) payload.size();
        int[] colors = payload.argb();
        for (int z = 0; z < payload.size(); z += sampleStep) {
            for (int x = 0; x < payload.size(); x += sampleStep) {
                int left = tileBounds.left() + (int) Math.floor(x * cellScale);
                int top = tileBounds.top() + (int) Math.floor(z * cellScale);
                int right = tileBounds.left() + (int) Math.ceil((x + sampleStep) * cellScale);
                int bottom = tileBounds.top() + (int) Math.ceil((z + sampleStep) * cellScale);
                int color = colors[z * payload.size() + x] | 0xFF000000;
                fillClipped(graphics, mapBounds, left, top, right, bottom, color);
            }
        }
    }

    private void drawStatusTile(GuiGraphics graphics, MapBounds mapBounds, UcsTerrainMapViewport.TileBounds tileBounds, int baseColor, int lineColor) {
        fillClipped(graphics, mapBounds, tileBounds.left(), tileBounds.top(), tileBounds.right(), tileBounds.bottom(), baseColor);
        for (int offset = 0; offset <= tileBounds.width(); offset += 16) {
            fillClipped(graphics, mapBounds, tileBounds.left() + offset, tileBounds.top(), tileBounds.left() + offset + 1, tileBounds.bottom(), lineColor);
            fillClipped(graphics, mapBounds, tileBounds.left(), tileBounds.top() + offset, tileBounds.right(), tileBounds.top() + offset + 1, lineColor);
        }
        drawTileBorder(graphics, mapBounds, tileBounds, lineColor);
    }

    private void drawTileBorder(GuiGraphics graphics, MapBounds mapBounds, UcsTerrainMapViewport.TileBounds tileBounds, int color) {
        fillClipped(graphics, mapBounds, tileBounds.left(), tileBounds.top(), tileBounds.right(), tileBounds.top() + 1, color);
        fillClipped(graphics, mapBounds, tileBounds.left(), tileBounds.bottom() - 1, tileBounds.right(), tileBounds.bottom(), color);
        fillClipped(graphics, mapBounds, tileBounds.left(), tileBounds.top(), tileBounds.left() + 1, tileBounds.bottom(), color);
        fillClipped(graphics, mapBounds, tileBounds.right() - 1, tileBounds.top(), tileBounds.right(), tileBounds.bottom(), color);
    }

    private void renderPlayerMarker(GuiGraphics graphics, MapBounds bounds) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.level().dimension().location().toString().equals(dimension)) {
            return;
        }
        int scale = UcsTerrainMapViewport.blockScale(zoom);
        int x = (int) Math.round(bounds.centerX() + (player.getX() - centerBlockX) / scale);
        int y = (int) Math.round(bounds.centerY() + (player.getZ() - centerBlockZ) / scale);
        if (!bounds.contains(x, y)) {
            return;
        }
        graphics.fill(x - 1, y - 7, x + 2, y + 8, 0xFFFFFFFF);
        graphics.fill(x - 7, y - 1, x + 8, y + 2, 0xFFFFFFFF);
        graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF2EC8A6);
    }

    private void renderFrame(GuiGraphics graphics, MapBounds bounds) {
        graphics.fill(bounds.left() - 1, bounds.top() - 1, bounds.right() + 1, bounds.top(), 0xFF3D4856);
        graphics.fill(bounds.left() - 1, bounds.bottom(), bounds.right() + 1, bounds.bottom() + 1, 0xFF3D4856);
        graphics.fill(bounds.left() - 1, bounds.top() - 1, bounds.left(), bounds.bottom() + 1, 0xFF3D4856);
        graphics.fill(bounds.right(), bounds.top() - 1, bounds.right() + 1, bounds.bottom() + 1, 0xFF3D4856);
    }

    private void renderHeader(GuiGraphics graphics, MapBounds bounds) {
        graphics.fill(0, 0, width, HEADER_HEIGHT, 0xF00D1117);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height, 0xF00D1117);
        graphics.drawString(font, title, 12, 6, 0xFFEAF2F8, false);

        Component status = Component.translatable(
                "screen.ucs.map.status",
                abbreviate(dimension, width < 420 ? 24 : 48),
                zoom,
                Math.round(centerBlockX),
                Math.round(centerBlockZ)
        );
        graphics.drawString(font, status, 12, 18, 0xFFB6C2CF, false);

        if (width > 420) {
            Component tileStatus = Component.translatable(
                    "screen.ucs.map.tiles",
                    requestedTiles.size(),
                    activeRequestId < 0 ? "-" : Integer.toString(activeRequestId)
            );
            int textWidth = font.width(tileStatus);
            graphics.drawString(font, tileStatus, Math.max(12, width - textWidth - 12), 18, 0xFFB6C2CF, false);
        }

        Component footer = Component.translatable("screen.ucs.map.footer", visibleTiles(bounds).size());
        graphics.drawString(font, footer, 12, height - 13, 0xFF7F8B99, false);
    }

    private void fillClipped(GuiGraphics graphics, MapBounds bounds, int left, int top, int right, int bottom, int color) {
        int clippedLeft = Math.max(left, bounds.left());
        int clippedTop = Math.max(top, bounds.top());
        int clippedRight = Math.min(right, bounds.right());
        int clippedBottom = Math.min(bottom, bounds.bottom());
        if (clippedRight > clippedLeft && clippedBottom > clippedTop) {
            graphics.fill(clippedLeft, clippedTop, clippedRight, clippedBottom, color);
        }
    }

    private List<MapTileKey> visibleTiles(MapBounds bounds) {
        return UcsTerrainMapViewport.visibleTiles(
                dimension,
                zoom,
                centerBlockX,
                centerBlockZ,
                bounds.width(),
                bounds.height(),
                MAX_REQUEST_TILES
        );
    }

    private void cycleDimension(int offset) {
        int currentIndex = CYCLE_DIMENSIONS.indexOf(dimension);
        int nextIndex = currentIndex < 0 ? 0 : Math.floorMod(currentIndex + offset, CYCLE_DIMENSIONS.size());
        dimension = CYCLE_DIMENSIONS.get(nextIndex);
        requestedTiles = List.of();
    }

    private void recenterOnPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        dimension = player.level().dimension().location().toString();
        centerBlockX = player.getBlockX();
        centerBlockZ = player.getBlockZ();
        requestedTiles = List.of();
    }

    private double screenToBlockX(double screenX, MapBounds bounds) {
        return centerBlockX + (screenX - bounds.centerX()) * UcsTerrainMapViewport.blockScale(zoom);
    }

    private double screenToBlockZ(double screenY, MapBounds bounds) {
        return centerBlockZ + (screenY - bounds.centerY()) * UcsTerrainMapViewport.blockScale(zoom);
    }

    private boolean insideMap(double mouseX, double mouseY) {
        return mapBounds().contains((int) mouseX, (int) mouseY);
    }

    private MapBounds mapBounds() {
        int left = MAP_MARGIN;
        int top = HEADER_HEIGHT + 4;
        int right = Math.max(left + 1, width - MAP_MARGIN);
        int bottom = Math.max(top + 1, height - FOOTER_HEIGHT - 4);
        return new MapBounds(left, top, right, bottom);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(1, maxLength - 3)) + "...";
    }

    private record MapBounds(int left, int top, int right, int bottom) {
        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }

        double centerX() {
            return left + width() / 2.0D;
        }

        double centerY() {
            return top + height() / 2.0D;
        }

        boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
