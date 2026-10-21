package dev.mctest.mixin;

import dev.mctest.MctestClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void attackaura$filterBlocks(BlockState state, BlockPos pos, BlockRenderView world,
                                         MatrixStack matrices, VertexConsumer vertexConsumer,
                                         boolean cull, Random random,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!MctestClient.isXrayEnabled()) return;

        String id = state.getBlock().getTranslationKey();
        boolean ore = id.contains("_ore") || id.contains("ancient_debris");
        if (!ore) {
            cir.setReturnValue(false);
        }
    }
}
