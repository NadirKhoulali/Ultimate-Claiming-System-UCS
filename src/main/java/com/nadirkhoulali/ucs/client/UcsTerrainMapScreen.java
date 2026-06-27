package com.nadirkhoulali.ucs.client;

import com.nadirkhoulali.ucs.core.model.MapTileKey;
import com.nadirkhoulali.ucs.map.ClaimMapOverlayChunk;
import com.nadirkhoulali.ucs.map.ClaimMapOverlayEntry;
import com.nadirkhoulali.ucs.map.TerrainTileResponseStatus;
import com.nadirkhoulali.ucs.map.TerrainTileStreamResponse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class UcsTerrainMapScreen extends Screen {
    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 18;
    private static final int MAP_MARGIN = 10;
    private static final int MAX_REQUEST_TILES = 256;
    private static final int PAN_KEY_PIXELS = 96;
    private static final long REQUEST_DEBOUNCE_MILLIS = 90L;
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
    private String selectedClaimId = "";
    private long lastViewportInteractionMillis = Long.MIN_VALUE;
    private final UcsTerrainTileTextureCache textureCache = new UcsTerrainTileTextureCache(Minecraft.getInstance().getTextureManager());

    public UcsTerrainMapScreen(String dimension, int centerBlockX, int centerBlockZ, int zoom) {
        super(Component.translatable("screen.ucs.map.title"));
        this.dimension = dimension;
        this.centerBlockX = centerBlockX;
        this.centerBlockZ = centerBlockZ;
        this.zoom = UcsTerrainMapViewport.clampZoom(zoom);
    }

    @Override
    public void tick() {
        requestVisibleTiles(System.currentTimeMillis());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MapBounds bounds = mapBounds();
        List<MapTileKey> visibleTiles = visibleTiles(bounds);
        graphics.fill(0, 0, width, height, 0xFF0D1117);
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFF151A21);
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        try {
            renderTiles(graphics, bounds, visibleTiles);
            renderClaimOverlays(graphics, bounds);
            renderPlayerMarker(graphics, bounds);
        } finally {
            graphics.disableScissor();
        }
        renderFrame(graphics, bounds);
        renderHeader(graphics, bounds, mouseX, mouseY, visibleTiles.size());
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The terrain map draws its own full-screen backdrop; keep vanilla's world blur off it.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && insideMap(mouseX, mouseY)) {
            selectedClaimId = overlayAt(mouseX, mouseY, mapBounds())
                    .map(ClaimMapOverlayEntry::claimId)
                    .orElse("");
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && insideMap(mouseX, mouseY)) {
            int scale = UcsTerrainMapViewport.blockScale(zoom);
            centerBlockX -= dragX * scale;
            centerBlockZ -= dragY * scale;
            markViewportInteracted();
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
        markViewportInteracted();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int scale = UcsTerrainMapViewport.blockScale(zoom);
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
            centerBlockX -= (double) PAN_KEY_PIXELS * scale;
            markViewportInteracted();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
            centerBlockX += (double) PAN_KEY_PIXELS * scale;
            markViewportInteracted();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
            centerBlockZ -= (double) PAN_KEY_PIXELS * scale;
            markViewportInteracted();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_S) {
            centerBlockZ += (double) PAN_KEY_PIXELS * scale;
            markViewportInteracted();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) {
            zoom = UcsTerrainMapViewport.clampZoom(zoom - 1);
            markViewportInteracted();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            zoom = UcsTerrainMapViewport.clampZoom(zoom + 1);
            markViewportInteracted();
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
    public void removed() {
        textureCache.close();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void requestVisibleTiles(long nowMillis) {
        if (lastViewportInteractionMillis != Long.MIN_VALUE && nowMillis - lastViewportInteractionMillis < REQUEST_DEBOUNCE_MILLIS) {
            return;
        }
        MapBounds bounds = mapBounds();
        List<MapTileKey> visibleTiles = visibleTiles(bounds);
        if (visibleTiles.equals(requestedTiles)) {
            return;
        }
        cancelActiveRequest();
        activeRequestId = UcsTerrainMapClient.nextRequestId();
        requestedTiles = visibleTiles;
        boolean requestedTerrain = UcsTerrainMapClient.requestTiles(activeRequestId, visibleTiles);
        boolean requestedOverlays = UcsTerrainMapClient.requestClaimOverlays(activeRequestId, dimension, visibleTiles);
        if (!requestedTerrain && !requestedOverlays) {
            activeRequestId = -1;
        }
    }

    private void markViewportInteracted() {
        lastViewportInteractionMillis = System.currentTimeMillis();
    }

    private void cancelActiveRequest() {
        if (activeRequestId >= 0) {
            UcsTerrainMapClient.cancelRequest(activeRequestId);
            activeRequestId = -1;
        }
    }

    private void renderTiles(GuiGraphics graphics, MapBounds bounds, List<MapTileKey> visibleTiles) {
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
        if (tile.status() == TerrainTileResponseStatus.HIT || tile.status() == TerrainTileResponseStatus.GENERATED) {
            try {
                UcsTerrainTileTextureCache.TileTexture texture = textureCache.textureFor(tile);
                if (texture != null) {
                    drawTerrainTexture(graphics, tileBounds, texture);
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

    private void renderClaimOverlays(GuiGraphics graphics, MapBounds bounds) {
        if (activeRequestId < 0) {
            return;
        }
        List<ClaimMapOverlayEntry> entries = UcsClaimOverlayClientCache.entries(activeRequestId, dimension);
        int visibleOverlayChunks = 0;
        for (ClaimMapOverlayEntry entry : entries) {
            visibleOverlayChunks += entry.chunks().size();
        }
        int chunkPixelSize = Math.max(1, (int) Math.round(16.0D / UcsTerrainMapViewport.blockScale(zoom)));
        boolean drawChunkDetails = visibleOverlayChunks <= 2048 && chunkPixelSize >= 4;
        boolean drawMarketAccents = chunkPixelSize >= 3;

        for (ClaimMapOverlayEntry entry : entries) {
            for (ClaimMapOverlayChunk chunk : entry.chunks()) {
                ChunkScreenBounds chunkBounds = chunkScreenBounds(chunk, bounds);
                fillClipped(graphics, bounds, chunkBounds.left(), chunkBounds.top(), chunkBounds.right(), chunkBounds.bottom(), entry.fillColor());
                if (drawChunkDetails) {
                    drawChunkBorder(graphics, bounds, chunkBounds, entry.borderColor());
                }
                if (drawMarketAccents && entry.forSale()) {
                    fillClipped(graphics, bounds, chunkBounds.left(), chunkBounds.top(), chunkBounds.right(), chunkBounds.top() + 2, entry.saleAccentColor());
                }
                if (drawMarketAccents && entry.leased()) {
                    fillClipped(graphics, bounds, chunkBounds.left(), chunkBounds.bottom() - 2, chunkBounds.right(), chunkBounds.bottom(), entry.leaseAccentColor());
                }
            }
        }
    }

    private void drawChunkBorder(GuiGraphics graphics, MapBounds mapBounds, ChunkScreenBounds chunkBounds, int color) {
        fillClipped(graphics, mapBounds, chunkBounds.left(), chunkBounds.top(), chunkBounds.right(), chunkBounds.top() + 1, color);
        fillClipped(graphics, mapBounds, chunkBounds.left(), chunkBounds.bottom() - 1, chunkBounds.right(), chunkBounds.bottom(), color);
        fillClipped(graphics, mapBounds, chunkBounds.left(), chunkBounds.top(), chunkBounds.left() + 1, chunkBounds.bottom(), color);
        fillClipped(graphics, mapBounds, chunkBounds.right() - 1, chunkBounds.top(), chunkBounds.right(), chunkBounds.bottom(), color);
    }

    private void drawTerrainTexture(GuiGraphics graphics, UcsTerrainMapViewport.TileBounds tileBounds, UcsTerrainTileTextureCache.TileTexture texture) {
        ResourceLocation location = texture.location();
        graphics.blit(
                location,
                tileBounds.left(),
                tileBounds.top(),
                tileBounds.width(),
                tileBounds.height(),
                0.0F,
                0.0F,
                texture.size(),
                texture.size(),
                texture.size(),
                texture.size()
        );
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

    private void renderHeader(GuiGraphics graphics, MapBounds bounds, int mouseX, int mouseY, int visibleTileCount) {
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

        Optional<ClaimMapOverlayEntry> hoverOverlay = isViewportSettling()
                ? Optional.empty()
                : overlayAt(mouseX, mouseY, bounds);
        String footer = hoverOverlay
                .or(() -> selectedOverlay(activeRequestId, dimension))
                .map(this::overlayFooter)
                .orElse(Component.translatable("screen.ucs.map.footer", visibleTileCount).getString());
        graphics.drawString(font, abbreviate(footer, Math.max(12, width / 6)), 12, height - 13, 0xFF7F8B99, false);
    }

    private void fillClipped(GuiGraphics graphics, MapBounds bounds, int left, int top, int right, int bottom, int color) {
        if (right > bounds.left() && left < bounds.right() && bottom > bounds.top() && top < bounds.bottom()) {
            graphics.fill(left, top, right, bottom, color);
        }
    }

    private boolean isViewportSettling() {
        return lastViewportInteractionMillis != Long.MIN_VALUE
                && System.currentTimeMillis() - lastViewportInteractionMillis < REQUEST_DEBOUNCE_MILLIS;
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

    private Optional<ClaimMapOverlayEntry> overlayAt(double mouseX, double mouseY, MapBounds bounds) {
        if (activeRequestId < 0 || !bounds.contains((int) mouseX, (int) mouseY)) {
            return Optional.empty();
        }
        List<ClaimMapOverlayEntry> entries = UcsClaimOverlayClientCache.entries(activeRequestId, dimension);
        for (int entryIndex = entries.size() - 1; entryIndex >= 0; entryIndex--) {
            ClaimMapOverlayEntry entry = entries.get(entryIndex);
            for (ClaimMapOverlayChunk chunk : entry.chunks()) {
                if (chunkScreenBounds(chunk, bounds).contains((int) mouseX, (int) mouseY)) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ClaimMapOverlayEntry> selectedOverlay(int requestId, String dimension) {
        if (selectedClaimId.isBlank() || requestId < 0) {
            return Optional.empty();
        }
        return UcsClaimOverlayClientCache.entries(requestId, dimension).stream()
                .filter(entry -> entry.claimId().equals(selectedClaimId))
                .findFirst();
    }

    private String overlayFooter(ClaimMapOverlayEntry entry) {
        String market = entry.forSale()
                ? "sale"
                : entry.leased() ? "lease" : "-";
        return Component.translatable(
                "screen.ucs.map.overlay",
                entry.displayName(),
                entry.relation().name().toLowerCase(Locale.ROOT),
                entry.ownerType().name().toLowerCase(Locale.ROOT),
                entry.chunks().size(),
                market
        ).getString();
    }

    private ChunkScreenBounds chunkScreenBounds(ClaimMapOverlayChunk chunk, MapBounds bounds) {
        int scale = UcsTerrainMapViewport.blockScale(zoom);
        long minBlockX = (long) chunk.x() * 16L;
        long minBlockZ = (long) chunk.z() * 16L;
        long maxBlockX = minBlockX + 16L;
        long maxBlockZ = minBlockZ + 16L;
        int left = (int) Math.round(bounds.centerX() + (minBlockX - centerBlockX) / scale);
        int top = (int) Math.round(bounds.centerY() + (minBlockZ - centerBlockZ) / scale);
        int right = (int) Math.round(bounds.centerX() + (maxBlockX - centerBlockX) / scale);
        int bottom = (int) Math.round(bounds.centerY() + (maxBlockZ - centerBlockZ) / scale);
        if (right <= left) {
            right = left + 1;
        }
        if (bottom <= top) {
            bottom = top + 1;
        }
        return new ChunkScreenBounds(left, top, right, bottom);
    }

    private void cycleDimension(int offset) {
        int currentIndex = CYCLE_DIMENSIONS.indexOf(dimension);
        int nextIndex = currentIndex < 0 ? 0 : Math.floorMod(currentIndex + offset, CYCLE_DIMENSIONS.size());
        dimension = CYCLE_DIMENSIONS.get(nextIndex);
        requestedTiles = List.of();
        markViewportInteracted();
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
        markViewportInteracted();
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

    private record ChunkScreenBounds(int left, int top, int right, int bottom) {
        boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }
}
