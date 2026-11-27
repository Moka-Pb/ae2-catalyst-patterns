package io.github.zero_pb.ae2catalyst;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import io.github.zero_pb.ae2catalyst.network.SetCatalystFlagsPacket;
import io.github.zero_pb.ae2catalyst.network.SetCatalystsPacket;
import io.github.zero_pb.ae2catalyst.network.SyncCatalystFlagsPacket;
import io.github.zero_pb.ae2catalyst.network.ToggleCatalystSlotPacket;
import io.github.zero_pb.ae2catalyst.pattern.CatalystPatternItem;
import io.github.zero_pb.ae2catalyst.recipe.ConvertPatternRecipe;

/**
 * AE2 Catalyst Patterns (name is provisional).
 *
 * Lets any ingredient of an AE2 processing pattern be marked as a "catalyst":
 * something the recipe needs at the machine but does not consume. AE2's
 * crafting planner then counts the catalyst at most once per pattern instead
 * of once per operation and never requires or crafts one. At execution time
 * a catalyst that was allocated from ME storage is delivered to the machine
 * with the first operation, and once no running job needs it anymore it is
 * returned to the network (see {@link CatalystReturns}).
 */
@Mod(Ae2Catalyst.MODID)
public class Ae2Catalyst {
    public static final String MODID = "ae2catalyst";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<Item> CATALYST_PATTERN = ITEMS.register("catalyst_pattern",
            () -> new CatalystPatternItem(new Item.Properties()));

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister
            .create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
    public static final RegistryObject<RecipeSerializer<?>> CONVERT_PATTERN = RECIPE_SERIALIZERS
            .register("convert_pattern", () -> new SimpleCraftingRecipeSerializer<>(ConvertPatternRecipe::new));

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"), () -> "1", "1"::equals, "1"::equals);

    public Ae2Catalyst() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        RECIPE_SERIALIZERS.register(bus);

        CHANNEL.registerMessage(0, SetCatalystsPacket.class, SetCatalystsPacket::encode,
                SetCatalystsPacket::decode, SetCatalystsPacket::handle);
        CHANNEL.registerMessage(1, ToggleCatalystSlotPacket.class, ToggleCatalystSlotPacket::encode,
                ToggleCatalystSlotPacket::decode, ToggleCatalystSlotPacket::handle);
        CHANNEL.registerMessage(2, SetCatalystFlagsPacket.class, SetCatalystFlagsPacket::encode,
                SetCatalystFlagsPacket::decode, SetCatalystFlagsPacket::handle);
        CHANNEL.registerMessage(3, SyncCatalystFlagsPacket.class, SyncCatalystFlagsPacket::encode,
                SyncCatalystFlagsPacket::decode, SyncCatalystFlagsPacket::handle);

        MinecraftForge.EVENT_BUS.addListener(CatalystReturns::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(CatalystReturns::onServerStopped);
    }
}
