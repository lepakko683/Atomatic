package atomatic.reference;

import thaumcraft.api.ItemApi;

import net.minecraft.item.ItemStack;

public class ThaumcraftReference
{
    public static final String MOD_ID = "thaumcraft";

    public static final ItemStack arcanePedestal = ItemApi.getBlock(Names.ThaumcraftBlocks.STONE_DEVICE, 1);

    public static final ItemStack fluxGas = ItemApi.getBlock(Names.ThaumcraftBlocks.FLUX_GAS, 7);
    public static final ItemStack fluxGoo = ItemApi.getBlock(Names.ThaumcraftBlocks.FLUX_GOO, 7);

    public static final ItemStack hole = ItemApi.getBlock(Names.ThaumcraftBlocks.HOLE, 15);

    public static final ItemStack wandCasting = ItemApi.getItem(Names.ThaumcraftItems.WAND_CASTING, 0);

    public static final ItemStack airShard = ItemApi.getItem(Names.ThaumcraftItems.SHARD, 0);
    public static final ItemStack fireShard = ItemApi.getItem(Names.ThaumcraftItems.SHARD, 1);
    public static final ItemStack waterShard = ItemApi.getItem(Names.ThaumcraftItems.SHARD, 2);
    public static final ItemStack earthShard = ItemApi.getItem(Names.ThaumcraftItems.SHARD, 3);
    public static final ItemStack orderShard = ItemApi.getItem(Names.ThaumcraftItems.SHARD, 4);
    public static final ItemStack entropyShard = ItemApi.getItem(Names.ThaumcraftItems.SHARD, 5);
    public static final ItemStack balancedShard = ItemApi.getItem(Names.ThaumcraftItems.SHARD, 6);

    public static final ItemStack magicSalt = ItemApi.getItem(Names.ThaumcraftItems.RESOURCE, 14);
    public static final ItemStack voidSeed = ItemApi.getItem(Names.ThaumcraftItems.RESOURCE, 17);

    public static final ItemStack primordialPearl = ItemApi.getItem(Names.ThaumcraftItems.ELDRITCH_OBJECT, 3);
}
