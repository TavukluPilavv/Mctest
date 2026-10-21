package dev.mctest.mixin;

import dev.mctest.MctestClient;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void attackaura$filterBlocks(BlockState state, BlockPos pos, BlockRenderView world,
                                         MatrixStack matrices, VertexConsumer vertexConsumer,
                                         boolean cull, Random random,
                                         CallbackInfo ci) {
        if (!MctestClient.isXrayEnabled()) return;

        // Use registry IDs, covering overworld/deepslate and Nether ore variants.
        String id = Registries.BLOCK.getId(state.getBlock()).getPath();
        boolean ore = id.endsWith("_ore") || id.equals("ancient_debris");
        if (!ore) ci.cancel();
    }
}
