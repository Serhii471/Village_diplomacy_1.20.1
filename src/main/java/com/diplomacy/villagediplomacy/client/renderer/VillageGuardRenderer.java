package com.diplomacy.villagediplomacy.client.renderer;

import com.diplomacy.villagediplomacy.entity.VillageGuardEntity;
import com.diplomacy.villagediplomacy.garrison.ResidentData;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public class VillageGuardRenderer extends HumanoidMobRenderer<VillageGuardEntity, PlayerModel<VillageGuardEntity>> {
    private static final ResourceLocation BASE = texture("base");
    private static final ResourceLocation PLAINS = texture("plains");
    private static final ResourceLocation MOUNTAIN = texture("mountain");
    private static final ResourceLocation FOREST = texture("forest");

    private static ResourceLocation texture(String variant) {
        return Objects.requireNonNull(ResourceLocation.tryParse(
                "villagediplomacy:textures/entity/guard/guard_" + variant + ".png"));
    }

    public VillageGuardRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(VillageGuardEntity entity) {
        ResourceLocation uniform = switch (entity.getUniformSpecialization()) {
            case PLAINS -> PLAINS;
            case MOUNTAIN -> MOUNTAIN;
            case FOREST -> FOREST;
            case UNASSIGNED -> BASE;
        };
        int face = entity.getResidentIdentity().map(ResidentData::face).orElse(0);
        return face == 0 ? uniform : Objects.requireNonNull(ResourceLocation.tryParse(
                uniform.toString().replace(".png", "_face_" + face + ".png")));
    }
}
