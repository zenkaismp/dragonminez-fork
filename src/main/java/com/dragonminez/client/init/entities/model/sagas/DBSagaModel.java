package com.dragonminez.client.init.entities.model.sagas;

import com.dragonminez.common.init.entities.ki.GumBindEntity;
import com.dragonminez.Reference;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

import java.util.HashMap;
import java.util.Map;

public class DBSagaModel<T extends DBSagasEntity> extends GeoModel<T> {

    private static final Map<ResourceLocation, Boolean> RESOURCE_CACHE = new HashMap<>();

    @Override
    public ResourceLocation getModelResource(T animatable) {
        ResourceLocation original = new ResourceLocation(Reference.MOD_ID, "geo/entity/sagas/" + animatable.getGeckolibModelName() + ".geo.json");

        boolean exists = RESOURCE_CACHE.computeIfAbsent(original, this::resourceExists);
        return exists ? original : new ResourceLocation(Reference.MOD_ID, "geo/entity/enemies/robotxv.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        String name = ForgeRegistries.ENTITY_TYPES.getKey(animatable.getType()).getPath();
        int variant = animatable.getTextureVariant();

        String variantSuffix = (variant == 0) ? "" : "_" + variant;
        ResourceLocation variantTexture = new ResourceLocation(Reference.MOD_ID, "textures/entity/sagas/" + name + variantSuffix + ".png");

        boolean variantExists = RESOURCE_CACHE.computeIfAbsent(variantTexture, this::resourceExists);
        if (variantExists) {
            return variantTexture;
        }

        if (variant > 0) {
            ResourceLocation baseTexture = new ResourceLocation(Reference.MOD_ID, "textures/entity/sagas/" + name + ".png");
            boolean baseExists = RESOURCE_CACHE.computeIfAbsent(baseTexture, this::resourceExists);
            if (baseExists) {
                return baseTexture;
            }
        }

        return new ResourceLocation(Reference.MOD_ID, "textures/entity/enemies/robotxv.png");
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return new ResourceLocation(Reference.MOD_ID, "animations/entity/sagas/saga_base.animation.json");
    }

    // v2.2 (5fe39df23, de03ed58c, d11e4b000): o NPC de saga acha nas animacoes do jogador o que nao esta no saga_base:
    // golpes de ki, golpes de strike, o agarrao (skp.grabbed), as esquivas e as dores (base.hurt_*, no movement).
    private static final ResourceLocation[] PLAYER_ANIMATION_FALLBACKS = {
            new ResourceLocation(Reference.MOD_ID, "animations/entity/races/ki.animation.json"),
            new ResourceLocation(Reference.MOD_ID, "animations/entity/races/skp.animation.json"),
            new ResourceLocation(Reference.MOD_ID, "animations/entity/races/evs.animation.json"),
            new ResourceLocation(Reference.MOD_ID, "animations/entity/races/movement.animation.json")
    };

    public ResourceLocation[] getAnimationResourceFallbacks(T animatable) {
        return PLAYER_ANIMATION_FALLBACKS;
    }

    // Zenkai: o GeckoLib 4.3.1 do fork nao tem o gancho getAnimationResourceFallbacks (4.4+). A mesma busca do
    // DMZPlayerModel: o arquivo principal e depois cada reserva.
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
    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        CoreGeoBone head = getAnimationProcessor().getBone("head");

        // v2.2 (698ca3f1f): preso pelo Gum Bind, o NPC nao gira a cabeca para onde olha.
        if (head != null && !GumBindEntity.isClientBound(animatable)) {
            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);

            head.setRotX(entityData.headPitch() * Mth.DEG_TO_RAD);
            head.setRotY(entityData.netHeadYaw() * Mth.DEG_TO_RAD);
        }
    }

    private boolean resourceExists(ResourceLocation location) {
        return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
    }
}