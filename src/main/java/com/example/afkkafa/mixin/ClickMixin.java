package com.example.afkkafa.mixin;

import com.example.afkkafa.AfkHeads;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClickMixin {

    @Inject(method = "clickSlot", at = @At("HEAD"), require = 0)
    private void afk$click(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        AfkHeads.onUserClick(slotId);
    }

    /** AFK kafa menusu aciksa olta atma / esya kullanma istegini (autofish dahil) engeller. */
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void afk$noUse(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (AfkHeads.isHeadMenu(MinecraftClient.getInstance())) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }
}
