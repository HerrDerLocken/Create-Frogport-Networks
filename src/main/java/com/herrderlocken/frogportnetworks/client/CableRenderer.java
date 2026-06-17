package com.herrderlocken.frogportnetworks.client;

import com.herrderlocken.frogportnetworks.CreateFrogportNetworks;
import com.herrderlocken.frogportnetworks.blockentity.NetworkCableBlockEntity;
import net.minecraft.world.item.DyeColor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix4f;

/**
 * CableRenderer — zeichnet die Kabel-Segmente eines Blocks dynamisch.
 *
 * Jede vorhandene Farbe wird als eigener, leicht versetzter Strang gerendert
 * (Kern + "Arme" zu verbundenen Nachbarn): Mantel in Typ-Farbe, Linie in der DyeColor.
 * So sind bis zu 4 Kabel im selben Block klar unterscheidbar. Gerendert wird
 * texturiert (Kabel-Textur) und per Vertex-Farbe getintet.
 */
public class CableRenderer implements BlockEntityRenderer<NetworkCableBlockEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateFrogportNetworks.MODID, "block/network_cable");

    public CableRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    private static final float STRIPE_HALF = 0.35f;

    @Override
    public void render(NetworkCableBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (be.isEmpty()) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(TEXTURE);

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Matrix4f matrix = poseStack.last().pose();

        for (DyeColor color : be.getColors()) {
            // Mantel in Typ-Farbe
            renderBoxes(matrix, vc, sprite, packedLight, be.getType(color).getBaseColor(),
                    be.buildSegmentBoxes(color, NetworkCableBlockEntity.HALF, 0f));

            // Farb-Linie (Netz-Kanal) — schmaler, minimal abstehend, damit sie obenauf liegt
            renderBoxes(matrix, vc, sprite, packedLight, color.getTextureDiffuseColor() & 0xFFFFFF,
                    be.buildSegmentBoxes(color, STRIPE_HALF, 0.15f));
        }
    }

    private static void renderBoxes(Matrix4f matrix, VertexConsumer vc, TextureAtlasSprite sprite,
                                    int light, int rgb, java.util.List<float[]> boxes) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        for (float[] box : boxes) {
            box(matrix, vc, sprite, r, g, b, light, box[0], box[1], box[2], box[3], box[4], box[5]);
        }
    }

    /** Zeichnet eine Box (alle Koordinaten in Pixel 0..16) als 6 getintete, texturierte Flächen. */
    private static void box(Matrix4f m, VertexConsumer vc, TextureAtlasSprite sprite,
                            int r, int g, int b, int light,
                            float px0, float py0, float pz0, float px1, float py1, float pz1) {
        float x0 = px0 / 16f, y0 = py0 / 16f, z0 = pz0 / 16f;
        float x1 = px1 / 16f, y1 = py1 / 16f, z1 = pz1 / 16f;
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();

        // unten (-Y) / oben (+Y)
        quad(m, vc, sprite, r, g, b, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, u0, u1, v0, v1);
        quad(m, vc, sprite, r, g, b, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0,  1, 0, u0, u1, v0, v1);
        // nord (-Z) / süd (+Z)
        quad(m, vc, sprite, r, g, b, light, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, 0, 0, -1, u0, u1, v0, v1);
        quad(m, vc, sprite, r, g, b, light, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0, 0,  1, u0, u1, v0, v1);
        // west (-X) / ost (+X)
        quad(m, vc, sprite, r, g, b, light, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1, 0, 0, u0, u1, v0, v1);
        quad(m, vc, sprite, r, g, b, light, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0,  1, 0, 0, u0, u1, v0, v1);
    }

    private static void quad(Matrix4f m, VertexConsumer vc, TextureAtlasSprite sprite,
                             int r, int g, int b, int light,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cxv, float cyv, float czv, float dx, float dy, float dz,
                             float nx, float ny, float nz,
                             float u0, float u1, float v0, float v1) {
        vertex(m, vc, r, g, b, light, ax, ay, az, nx, ny, nz, u0, v0);
        vertex(m, vc, r, g, b, light, bx, by, bz, nx, ny, nz, u0, v1);
        vertex(m, vc, r, g, b, light, cxv, cyv, czv, nx, ny, nz, u1, v1);
        vertex(m, vc, r, g, b, light, dx, dy, dz, nx, ny, nz, u1, v0);
    }

    private static void vertex(Matrix4f m, VertexConsumer vc, int r, int g, int b, int light,
                               float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        vc.addVertex(m, x, y, z)
                .setColor(r, g, b, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }

    public static BlockEntityRenderer<NetworkCableBlockEntity> create(BlockEntityRendererProvider.Context ctx) {
        return new CableRenderer(ctx);
    }
}
