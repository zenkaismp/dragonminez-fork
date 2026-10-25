package com.dragonminez.client.init.entities.model.sagas;

import com.dragonminez.Reference;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class DBSaibamanModel<T extends DBSagasEntity> extends GeoModel<T> {

    // v2.2 (d11e4b000): as dores do Saibaman vem do arquivo de movimento do jogador
    private static final ResourceLocation[] ANIMATION_FALLBACKS = {
            new ResourceLocation(Reference.MOD_ID, "animations/entity/races/movement.animation.json")
    };

    public ResourceLocation[] getAnimationResourceFallbacks(T animatable) {
        return ANIMATION_FALLBACKS;
    }

    // Zenkai: o GeckoLib 4.3.1 do fork nao tem o gancho getAnimationResourceFallbacks (4.4+). A mesma busca do
    // DBSagaModel: o arquivo principal e depois cada reserva.
    @Override
    public software.bernie.geckolib.core.animation.Animation getAnimation(T animatable, String name) {
        java.util.Map<ResourceLocation, software.bernie.geckolib.loading.object.BakedAnimations> cache =
                software.bernie.geckolib.cache.GeckoLibCache.getBakedAnimations();
        software.bernie.geckolib.loading.object.BakedAnimations primary = cache.get(getAnimationResource(animatable));
        if (primary != null) {
            software.bernie.geckolib.core.animation.Animation anim = primary.getAnimation(name);
            if (anim != null) return anim;
        }
        for (ResourceLocation fallback : getAnimationResourceFallbacks(animatable)) {
            software.bernie.geckolib.loading.object.BakedAnimations fb = cache.get(fallback);
            if (fb != null) {
                software.bernie.geckolib.core.animation.Animation anim = fb.getAnimation(name);
                if (anim != null) return anim;
            }
        }
        return null;
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        String name = ForgeRegistries.ENTITY_TYPES.getKey(animatable.getType()).getPath();
        return new ResourceLocation(Reference.MOD_ID, "geo/entity/sagas/saga_saibaman.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        String name = ForgeRegistries.ENTITY_TYPES.getKey(animatable.getType()).getPath();
        return new ResourceLocation(Reference.MOD_ID, "textures/entity/sagas/" + name + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        String name = ForgeRegistries.ENTITY_TYPES.getKey(animatable.getType()).getPath();
        return new ResourceLocation(Reference.MOD_ID, "animations/entity/sagas/saga_saibaman.animation.json");
    }

    @Override
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        CoreGeoBone head = getAnimationProcessor().getBone("head");

        if (head != null) {
            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);

            head.setRotX(entityData.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(entityData.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }

}
