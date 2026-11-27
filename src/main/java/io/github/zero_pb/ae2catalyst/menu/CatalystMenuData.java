package io.github.zero_pb.ae2catalyst.menu;

/**
 * Extra state carried by AE2's pattern encoding terminal menu (added by
 * mixin): one catalyst flag per sparse processing input slot, as marked by
 * the player in the terminal. Read at encode time to bake the flags into
 * the encoded pattern.
 */
public interface CatalystMenuData {
    boolean[] ae2catalyst$getCatalystFlags();
}
