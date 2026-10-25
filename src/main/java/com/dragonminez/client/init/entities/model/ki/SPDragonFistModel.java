package com.dragonminez.client.init.entities.model.ki;

import com.dragonminez.Reference;
import com.dragonminez.common.init.entities.ki.SPDragonFistEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.molang.MolangParser;
import software.bernie.geckolib.model.GeoModel;

public class SPDragonFistModel<T extends SPDragonFistEntity> extends GeoModel<T> {

    public static final int STRIKE_TICKS = 8;

    // Zenkai: o punho e o Shenron novo da v2.2 (geo/entity/dragon/shenron.geo.json de la). O Shenron dos desejos do fork
    // ainda e o modelo antigo, com outros ossos, entao o modelo da v2.2 vem com o nome do golpe, sem trocar o dragao.
    @Override
    public ResourceLocation getModelResource(T animatable) {
        return new ResourceLocation(Reference.MOD_ID, "geo/entity/skills/sp_dragonfist_shenron.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return new ResourceLocation(Reference.MOD_ID, "textures/entity/skills/sp_dragonfist_shenron.png");
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return new ResourceLocation(Reference.MOD_ID, "animations/entity/skills/sp_dragonfist_shenron.animation.json");
    }

    @Override
    public void applyMolangQueries(T animatable, double animTime) {
        super.applyMolangQueries(animatable, animTime);

        float age = animatable.tickCount + Minecraft.getInstance().getFrameTime();
        float strike = Mth.clamp((age - (animatable.getMaxLife() - STRIKE_TICKS)) / STRIKE_TICKS, 0.0f, 1.0f);
        float eased = strike * strike * (3.0f - 2.0f * strike);
        MolangParser.INSTANCE.setValue("variable.dmz_df_strike", () -> eased);
    }
}
