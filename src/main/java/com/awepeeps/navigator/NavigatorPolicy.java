package com.awepeeps.navigator;

/** Small pure interaction policy used directly by event handlers. */
public final class NavigatorPolicy {
  private NavigatorPolicy() {}

  public static boolean canUseCompass(boolean ordinaryCompass, int amount) {
    return ordinaryCompass && amount == 1;
  }

  public static boolean shouldPassRightClick(
      boolean clickedLodestone, boolean heldLodestoneCompass, boolean offhandFallback) {
    return clickedLodestone || heldLodestoneCompass || offhandFallback;
  }

  public static boolean isLodestoneCompass(boolean hasTarget, boolean hasTrackingComponent) {
    return hasTarget || hasTrackingComponent;
  }

  public static int remainingAfterCreation(boolean creationSaved, int remaining) {
    return creationSaved ? NavigationMath.afterSuccessfulUse(remaining) : remaining;
  }

  public static boolean mayOpenMenu(boolean hasRequiredItem, boolean requireItem) {
    return hasRequiredItem || !requireItem;
  }

  public static boolean mayCreate(boolean hasRequiredItem, boolean hasPermission, boolean bypassEnabled, boolean hasBypass) {
    return hasRequiredItem && (hasPermission || (bypassEnabled && hasBypass));
  }

  public static boolean mayOpenInteractionMenu(boolean sneakRightClickEnabled, boolean sneaking) {
    return !sneakRightClickEnabled || sneaking;
  }

  public static boolean shouldCharge(boolean durabilityEnabled, boolean managed) {
    return durabilityEnabled && managed;
  }
}
