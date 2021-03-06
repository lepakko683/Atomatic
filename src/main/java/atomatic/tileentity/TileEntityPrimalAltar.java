package atomatic.tileentity;

import atomatic.api.AtomaticApi;
import atomatic.api.IAltarRelay;
import atomatic.api.IRelay;
import atomatic.api.adjusting.AdjustEffect;
import atomatic.api.adjusting.Adjustment;
import atomatic.api.ICrystal;
import atomatic.api.primal.PrimalObject;
import atomatic.api.primal.PrimalRecipe;

import atomatic.init.ModItems;
import atomatic.item.ItemPrimalObject;
import atomatic.reference.Names;
import atomatic.reference.Sounds;
import atomatic.reference.ThaumcraftReference;
import atomatic.util.AdjustmentHelper;
import atomatic.util.InventoryHelper;
import atomatic.util.LogHelper;

import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.TileThaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.visnet.VisNetHandler;
import thaumcraft.api.wands.IWandable;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO Add the actual boost items
public class TileEntityPrimalAltar extends TileEntityA implements ISidedInventory, IWandable, IAspectContainer
{
    public static final int INVENTORY_SIZE = 1;
    public static final int SLOT_INVENTORY_INDEX = 0;
    public static final int[] SLOTS = new int[]{SLOT_INVENTORY_INDEX};
    public static final int INVENTORY_STACK_LIMIT = 1;
    public static final int CRYSTAL_OFFSET = 2;
    public static final int CLOSE_CRYSTAL_OFFSET = CRYSTAL_OFFSET - 1;
    public static final int PEDESTAL_OFFSET = CRYSTAL_OFFSET * 2;
    public static final int ALTAR_RELAY_OFFSET = 2;
    public static final int PEDESTAL_RELAY_OFFSET = 2;
    public static final int PEDESTAL_SLOT = 0;
    public static final int MAX_VIS_DRAIN = 20;
    public static final int FREQUENCY = 5;
    public static final int SOUND_FREQUENCY = 65;
    public static final int ADJUSTMENT_FREQUENCY = 100;

    private static final int TRUE = 1;
    private static final int FALSE = -TRUE;

    protected List<ChunkCoordinates> noAdjustmentAllowed;

    private ItemStack[] inventory;
    private int ticks = 0;
    private boolean crafting = false;
    private AspectList vis = new AspectList();
    private PrimalRecipe recipe = null;
    private EntityPlayer player = null;
    private List<ChunkCoordinates> pedestals = new ArrayList<ChunkCoordinates>();
    private List<ChunkCoordinates> crystals = new ArrayList<ChunkCoordinates>();
    private ChunkCoordinates primalPedestal = null;
    private boolean runningAdjustments = false;
    private int runningAdjustmentsTicks = 0;
    private int runningAdjustmentsCount = 0;

    public TileEntityPrimalAltar()
    {
        noAdjustmentAllowed = new ArrayList<ChunkCoordinates>();
        noAdjustmentAllowed.add(new ChunkCoordinates(xCoord + CRYSTAL_OFFSET, yCoord, zCoord));
        noAdjustmentAllowed.add(new ChunkCoordinates(xCoord - CRYSTAL_OFFSET, yCoord, zCoord));
        noAdjustmentAllowed.add(new ChunkCoordinates(xCoord, yCoord, zCoord + CRYSTAL_OFFSET));
        noAdjustmentAllowed.add(new ChunkCoordinates(xCoord, yCoord, zCoord - CRYSTAL_OFFSET));

        inventory = new ItemStack[INVENTORY_SIZE];
    }

    @Override
    protected void readNBT(NBTTagCompound nbtTagCompound)
    {
        NBTTagList tagList = nbtTagCompound.getTagList(Names.NBT.ITEMS, 10);
        inventory = new ItemStack[getSizeInventory()];

        for (int i = 0; i < tagList.tagCount(); ++i)
        {
            NBTTagCompound tagCompound = tagList.getCompoundTagAt(i);
            byte slotIndex = tagCompound.getByte(Names.NBT.SLOT);

            if (slotIndex >= 0 && slotIndex < inventory.length)
            {
                inventory[slotIndex] = ItemStack.loadItemStackFromNBT(tagCompound);
            }
        }

        ticks = nbtTagCompound.getInteger(Names.NBT.TICKS);
        crafting = nbtTagCompound.getBoolean(Names.NBT.CRAFTING);
        vis.readFromNBT(nbtTagCompound, Names.NBT.VIS);
        recipe = nbtTagCompound.getInteger(Names.NBT.RECIPE) == 0 ? null : AtomaticApi.getPrimalRecipeForHash(nbtTagCompound.getInteger(Names.NBT.RECIPE));
        player = nbtTagCompound.getString(Names.NBT.PLAYER).equals("") ? null : worldObj.getPlayerEntityByName(nbtTagCompound.getString(Names.NBT.PLAYER));

        NBTTagList pedestalList = nbtTagCompound.getTagList(Names.NBT.PEDESTALS, 10);
        pedestals = new ArrayList<ChunkCoordinates>();

        for (int i = 0; i < pedestalList.tagCount(); i++)
        {
            NBTTagCompound compound = pedestalList.getCompoundTagAt(i);
            byte index = compound.getByte(Names.NBT.INDEX);

            if (index >= 0 && index < pedestalList.tagCount())
            {
                int x = compound.getInteger(Names.NBT.POS_X);
                int y = compound.getInteger(Names.NBT.POS_Y);
                int z = compound.getInteger(Names.NBT.POS_Z);

                pedestals.add(index, new ChunkCoordinates(x, y, z));

                if (compound.getBoolean(Names.NBT.PRIMAL))
                {
                    primalPedestal = new ChunkCoordinates(x, y, z);
                }
            }
        }

        NBTTagList crystalList = nbtTagCompound.getTagList(Names.NBT.CRYSTALS, 10);
        crystals = new ArrayList<ChunkCoordinates>();

        for (int i = 0; i < crystalList.tagCount(); i++)
        {
            NBTTagCompound compound = crystalList.getCompoundTagAt(i);
            byte index = compound.getByte(Names.NBT.INDEX);

            if (index >= 0 && index < crystalList.tagCount())
            {
                int x = compound.getInteger(Names.NBT.POS_X);
                int y = compound.getInteger(Names.NBT.POS_Y);
                int z = compound.getInteger(Names.NBT.POS_Z);

                crystals.add(index, new ChunkCoordinates(x, y, z));
            }
        }

        runningAdjustments = nbtTagCompound.getBoolean(Names.NBT.RUNNING_ADJUSTMENTS);
        runningAdjustmentsTicks = nbtTagCompound.getInteger(Names.NBT.RUNNING_ADJUSTMENTS_TICKS);
        runningAdjustmentsCount = nbtTagCompound.getInteger(Names.NBT.RUNNING_ADJUSTMENTS_COUNT);
    }

    @Override
    protected void writeNBT(NBTTagCompound nbtTagCompound)
    {
        NBTTagList items = new NBTTagList();

        for (int currentIndex = 0; currentIndex < inventory.length; ++currentIndex)
        {
            if (inventory[currentIndex] != null)
            {
                NBTTagCompound tagCompound = new NBTTagCompound();
                tagCompound.setByte(Names.NBT.SLOT, (byte) currentIndex);
                inventory[currentIndex].writeToNBT(tagCompound);
                items.appendTag(tagCompound);
            }
        }

        nbtTagCompound.setTag(Names.NBT.ITEMS, items);
        nbtTagCompound.setInteger(Names.NBT.TICKS, ticks);
        nbtTagCompound.setBoolean(Names.NBT.CRAFTING, crafting);
        vis.writeToNBT(nbtTagCompound, Names.NBT.VIS);
        nbtTagCompound.setInteger(Names.NBT.RECIPE, recipe == null ? 0 : recipe.hashCode());
        nbtTagCompound.setString(Names.NBT.PLAYER, (player == null ? "" : player.getCommandSenderName()));

        NBTTagList pedestalList = new NBTTagList();

        for (int i = 0; i < pedestals.size(); i++)
        {
            if (pedestals.get(i) != null)
            {
                NBTTagCompound compound = new NBTTagCompound();
                compound.setByte(Names.NBT.INDEX, (byte) i);
                compound.setInteger(Names.NBT.POS_X, pedestals.get(i).posX);
                compound.setInteger(Names.NBT.POS_Y, pedestals.get(i).posY);
                compound.setInteger(Names.NBT.POS_Z, pedestals.get(i).posZ);

                if (pedestals.get(i).equals(primalPedestal))
                {
                    compound.setBoolean(Names.NBT.PRIMAL, true);
                }
                else
                {
                    compound.setBoolean(Names.NBT.PRIMAL, false);
                }

                pedestalList.appendTag(compound);
            }
        }

        nbtTagCompound.setTag(Names.NBT.PEDESTALS, pedestalList);

        NBTTagList crystalList = new NBTTagList();

        for (int i = 0; i < crystals.size(); i++)
        {
            if (crystals.get(i) != null)
            {
                NBTTagCompound compound = new NBTTagCompound();
                compound.setByte(Names.NBT.INDEX, (byte) i);
                compound.setInteger(Names.NBT.POS_X, crystals.get(i).posX);
                compound.setInteger(Names.NBT.POS_Y, crystals.get(i).posY);
                compound.setInteger(Names.NBT.POS_Z, crystals.get(i).posZ);
                pedestalList.appendTag(compound);
            }
        }

        nbtTagCompound.setTag(Names.NBT.CRYSTALS, crystalList);
        nbtTagCompound.setBoolean(Names.NBT.RUNNING_ADJUSTMENTS, runningAdjustments);
        nbtTagCompound.setInteger(Names.NBT.RUNNING_ADJUSTMENTS_TICKS, runningAdjustmentsTicks);
        nbtTagCompound.setInteger(Names.NBT.RUNNING_ADJUSTMENTS_COUNT, runningAdjustmentsCount);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTagCompound)
    {
        super.readFromNBT(nbtTagCompound);
        readNBT(nbtTagCompound);
        logEvent("NBT read");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbtTagCompound)
    {
        super.writeToNBT(nbtTagCompound);
        writeNBT(nbtTagCompound);
        logEvent("NBT written");
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void updateEntity()
    {
        boolean needsUpdate = false;

        if (crafting)
        {
            ++ticks;
            logEvent("Tick " + ticks);
        }

        if (worldObj.isRemote)
        {
            if (crafting)
            {
                if (ticks == 0)
                {
                    logEvent("Playing sound " + Sounds.THAUMCRAFT_INFUSER_START + " at tick " + ticks);
                    worldObj.playSound((double) xCoord, (double) yCoord, (double) zCoord, Sounds.THAUMCRAFT_INFUSER_START, 0.5F, 2.0F, false);
                }
                else if (ticks % SOUND_FREQUENCY == 0)
                {
                    logEvent("Playing sound " + Sounds.THAUMCRAFT_INFUSER + " at tick " + ticks);
                    worldObj.playSound((double) xCoord, (double) yCoord, (double) zCoord, Sounds.THAUMCRAFT_INFUSER, 0.5F, 2.0F, false);
                }
            }
        }
        else
        {
            if (crafting && canCraft())
            {
                if (ticks % ADJUSTMENT_FREQUENCY == 0)
                {
                    logEvent("Started running adjustments at tick " + ticks);
                    runningAdjustments = true;
                    needsUpdate = true;
                }

                boolean stabilize = false;
                int stabilizeAmount = 0;

                boolean moreMaxDrain = false;
                int moreMaxDrainAmount = 0;

                if (runningAdjustmentsCount < getRuntimeAdjustments().size())
                {
                    if (runningAdjustments && getRuntimeAdjustments() != null)
                    {
                        ++runningAdjustmentsTicks;
                        logEvent("Running adjustments tick " + ticks);

                        if (runningAdjustmentsTicks % FREQUENCY == 0)
                        {
                            logEvent("Running adjustment at running tick " + runningAdjustmentsTicks + " and at tick " + ticks);

                            List<Map.Entry<ChunkCoordinates, Adjustment>> entries = Lists.newArrayList(getRuntimeAdjustments().entrySet());
                            Map.Entry<ChunkCoordinates, Adjustment> entry = entries.get(runningAdjustmentsCount);
                            ChunkCoordinates coordinates = entry.getKey();
                            Adjustment adjustment = entry.getValue();
                            boolean found = false;

                            if (adjustment.effect == AdjustEffect.STABILIZE)
                            {
                                stabilize = true;
                                stabilizeAmount = adjustment.strength;
                                found = true;
                            }
                            else if (adjustment.effect == AdjustEffect.MORE_MAX_DRAIN)
                            {
                                moreMaxDrain = true;
                                moreMaxDrainAmount = adjustment.strength;
                                found = true;
                            }

                            if (found)
                            {
                                if (isPedestal(coordinates.posX, coordinates.posY, coordinates.posZ))
                                {
                                    getPedestalInventory(coordinates.posX, coordinates.posY, coordinates.posZ).setInventorySlotContents(PEDESTAL_SLOT, null);
                                    worldObj.markBlockForUpdate(coordinates.posX, coordinates.posY, coordinates.posZ);
                                    getPedestal(coordinates.posX, coordinates.posY, coordinates.posZ).markDirty();
                                }
                            }

                            ++runningAdjustmentsCount;
                            needsUpdate = true;
                        }
                    }
                }
                else
                {
                    runningAdjustments = false;
                    needsUpdate = true;
                }

                int maxInstability = 10000 + ((crystals.size() - 1) * 10);

                if (stabilize)
                {
                    maxInstability += stabilizeAmount;
                }

                int random = worldObj.rand.nextInt(maxInstability);

                if (random > 432 && random < 450)
                {
                    worldObj.createExplosion(null, (double) (float) xCoord, (double) ((float) yCoord + 0.5F), (double) ((float) zCoord), 1.2F + worldObj.rand.nextFloat(), false);
                }
                else if (random < 5)
                {
                    worldObj.setBlock(xCoord, yCoord + 1, zCoord, Block.getBlockFromItem(ThaumcraftReference.fluxGas.getItem()), ThaumcraftReference.fluxGas.getItemDamage(), 3);
                }
                else if (random > 9998 && random < 10001)
                {
                    List entities = worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getBoundingBox((double) xCoord, (double) yCoord, (double) zCoord, (double) (xCoord + 1), (double) (yCoord + 1), (double) (zCoord + 1)).expand(9.0D, 9.0D, 9.0D));

                    if (entities != null && entities.size() > 0)
                    {
                        for (Object entity : entities)
                        {
                            if (worldObj.rand.nextFloat() < 0.25F)
                            {
                                ThaumcraftApiHelper.addStickyWarpToPlayer((EntityPlayer) entity, 2);
                            }
                            else
                            {
                                ThaumcraftApiHelper.addWarpToPlayer((EntityPlayer) entity, 2 + worldObj.rand.nextInt(7), true);
                            }
                        }
                    }
                }

                if (ticks % FREQUENCY == 0)
                {
                    int maxDrain = MAX_VIS_DRAIN;

                    if (moreMaxDrain)
                    {
                        maxDrain += moreMaxDrainAmount;
                    }

                    logEvent("Attempting to drain vis at tick " + ticks);
                    Aspect aspect = vis.getAspectsSortedAmount()[vis.getAspectsSortedAmount().length - 1];
                    int visDrain = VisNetHandler.drainVis(worldObj, xCoord, yCoord, zCoord, aspect, Math.min(maxDrain, vis.getAmount(aspect)));

                    if (visDrain > 0)
                    {
                        logEvent("Drained " + visDrain + " " + aspect.getTag() + " vis");
                        vis.reduce(aspect, visDrain);
                        needsUpdate = true;
                    }
                }

                if (vis.visSize() <= 0)
                {
                    getPrimalPedestalInventory().setInventorySlotContents(PEDESTAL_SLOT, null);
                    getPrimalPedestal().getWorldObj().markBlockForUpdate(primalPedestal.posX, primalPedestal.posY, primalPedestal.posZ);
                    getPrimalPedestal().markDirty();

                    inventory[SLOT_INVENTORY_INDEX] = recipe.getOutput().copy();

                    // TODO Find better sound worldObj.playSoundEffect((double) xCoord, (double) yCoord, (double) zCoord, Sounds.RANDOM_FIZZ, 0.9F, 1.0F);

                    if (ThaumcraftApi.getWarp(recipe.getOutput().copy()) > 0)
                    {
                        ThaumcraftApiHelper.addStickyWarpToPlayer(player, ThaumcraftApi.getWarp(recipe.getOutput().copy()));
                    }

                    logEvent("Crafting finished");

                    ticks = 0;
                    crafting = false;
                    vis = new AspectList();
                    recipe = null;
                    player = null;
                    runningAdjustments = false;
                    runningAdjustmentsTicks = 0;
                    runningAdjustmentsCount = 0;
                    needsUpdate = true;
                }
            }

            if (crafting && !canCraft())
            {
                List entities = worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getBoundingBox((double) xCoord, (double) yCoord, (double) zCoord, (double) (xCoord + 1), (double) (yCoord + 1), (double) (zCoord + 1)).expand(9.0D, 9.0D, 9.0D));

                if (entities != null && entities.size() > 0)
                {
                    for (Object entity : entities)
                    {
                        if (worldObj.rand.nextFloat() < 0.25F)
                        {
                            ThaumcraftApiHelper.addStickyWarpToPlayer((EntityPlayer) entity, 4);
                        }
                        else
                        {
                            ThaumcraftApiHelper.addWarpToPlayer((EntityPlayer) entity, 4 + worldObj.rand.nextInt(6), true);
                        }
                    }
                }

                ticks = 0;
                crafting = false;
                vis = new AspectList();
                recipe = null;
                player = null;
                runningAdjustments = false;
                runningAdjustmentsTicks = 0;
                runningAdjustmentsCount = 0;
                worldObj.playSoundEffect((double) xCoord, (double) yCoord, (double) zCoord, Sounds.THAUMCRAFT_CRAFT_FAIL, 1.0F, 0.6F);
                worldObj.createExplosion(null, (double) (float) xCoord, (double) ((float) yCoord + 0.5F), (double) ((float) zCoord), 4.0F, true);
                needsUpdate = true;
            }
        }

        if (needsUpdate)
        {
            update();
        }
    }

    @Override
    public int getSizeInventory()
    {
        return inventory.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot)
    {
        return inventory[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount)
    {
        if (inventory[slot] != null)
        {
            if (!worldObj.isRemote)
            {
                markThisForUpdate();
            }

            ItemStack stack;

            if (inventory[slot].stackSize <= amount)
            {
                stack = inventory[slot];
                inventory[slot] = null;
            }
            else
            {
                stack = inventory[slot].splitStack(amount);

                if (inventory[slot].stackSize == 0)
                {
                    inventory[slot] = null;
                }
            }

            markDirty();

            return stack;
        }

        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot)
    {
        if (inventory[slot] != null)
        {
            ItemStack stack = inventory[slot];
            inventory[slot] = null;

            return stack;
        }

        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack)
    {
        inventory[slot] = stack;

        if (stack != null && stack.stackSize > getInventoryStackLimit())
        {
            stack.stackSize = getInventoryStackLimit();
        }

        markDirty();

        if (!worldObj.isRemote)
        {
            markThisForUpdate();
        }

        logEvent("Inventory slot contents set (" + (inventory[slot] == null ? "null" : inventory[slot].toString()) + ")");
    }

    @Override
    public String getInventoryName()
    {
        return hasCustomName() ? getCustomName() : Names.Containers.PRIMAL_ALTAR;
    }

    @Override
    public boolean hasCustomInventoryName()
    {
        return hasCustomName();
    }

    @Override
    public int getInventoryStackLimit()
    {
        return INVENTORY_STACK_LIMIT;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player)
    {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this && player.getDistanceSq((double) xCoord + 0.5D, (double) yCoord + 0.5D, (double) zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory()
    {

    }

    @Override
    public void closeInventory()
    {

    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack)
    {
        return true;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side)
    {
        return SLOTS;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side)
    {
        return inventory[slot] == null;
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side)
    {
        return true;
    }

    @Override
    public int onWandRightClick(World world, ItemStack wandstack, EntityPlayer entityPlayer, int x, int y, int z, int side, int md)
    {
        if (!worldObj.isRemote)
        {
            logEvent("Wanded");

            if (entityPlayer.isSneaking())
            {
                if (getStackInSlot(PEDESTAL_SLOT) != null)
                {
                    dropItemsAtEntity(entityPlayer);
                    world.playSoundEffect((double) x, (double) y, (double) z, Sounds.RANDOM_POP, 0.2F, ((world.rand.nextFloat() - world.rand.nextFloat()) * 0.7F + 1.0F) * 1.5F);
                    return TRUE;
                }

                if (getStackInSlot(PEDESTAL_SLOT) == null)
                {
                    ItemStack stack = entityPlayer.getCurrentEquippedItem().copy();
                    stack.stackSize = 1;
                    setInventorySlotContents(SLOT_INVENTORY_INDEX, stack);
                    --entityPlayer.getCurrentEquippedItem().stackSize;

                    if (entityPlayer.getCurrentEquippedItem().stackSize <= 0)
                    {
                        entityPlayer.setCurrentItemOrArmor(0, null);
                    }

                    entityPlayer.inventory.markDirty();
                    world.playSoundEffect((double) x, (double) y, (double) z, Sounds.RANDOM_POP, 0.2F, ((world.rand.nextFloat() - world.rand.nextFloat()) * 0.7F + 1.0F) * 1.6F);
                    return TRUE;
                }
            }

            if (recipe != null && crafting)
            {
                if (recipe.getStartingAspectsForWand() != null)
                {
                    AspectList aspectList = new AspectList();

                    for (int i = 0; i < recipe.getStartingAspectsForWand().getAspectsSorted().length; i++)
                    {
                        aspectList.add(recipe.getStartingAspectsForWand().getAspectsSorted()[i], recipe.getStartingAspectsForWand().getAmount(recipe.getStartingAspectsForWand().getAspectsSorted()[i]) * 2);
                    }

                    ThaumcraftApiHelper.consumeVisFromWand(wandstack, entityPlayer, aspectList, true, false);
                }

                if (AtomaticApi.getPrimalRecipeWarp(recipe) > 0)
                {
                    ThaumcraftApiHelper.addWarpToPlayer(entityPlayer, AtomaticApi.getPrimalRecipeWarp(recipe) * 2, true);
                }

                for (ChunkCoordinates coordinates : pedestals)
                {
                    worldObj.setBlock(coordinates.posX, coordinates.posY + 1, coordinates.posZ, Block.getBlockFromItem(ThaumcraftReference.fluxGoo.getItem()), ThaumcraftReference.fluxGoo.getItemDamage(), 3);
                }

                if (worldObj.isAirBlock(xCoord, yCoord + 1, zCoord))
                {
                    worldObj.setBlock(xCoord, yCoord + 1, zCoord, Block.getBlockFromItem(ThaumcraftReference.fluxGoo.getItem()), ThaumcraftReference.fluxGoo.getItemDamage(), 3);
                }

                if (worldObj.isAirBlock(xCoord + 1, yCoord, zCoord))
                {
                    worldObj.setBlock(xCoord + 1, yCoord, zCoord, Block.getBlockFromItem(ThaumcraftReference.fluxGas.getItem()), ThaumcraftReference.fluxGas.getItemDamage(), 3);
                }

                if (worldObj.isAirBlock(xCoord - 1, yCoord, zCoord))
                {
                    worldObj.setBlock(xCoord - 1, yCoord, zCoord, Block.getBlockFromItem(ThaumcraftReference.fluxGas.getItem()), ThaumcraftReference.fluxGas.getItemDamage(), 3);
                }

                if (worldObj.isAirBlock(xCoord, yCoord, zCoord + 1))
                {
                    worldObj.setBlock(xCoord, yCoord, zCoord + 1, Block.getBlockFromItem(ThaumcraftReference.fluxGas.getItem()), ThaumcraftReference.fluxGas.getItemDamage(), 3);
                }

                if (worldObj.isAirBlock(xCoord, yCoord, zCoord - 1))
                {
                    worldObj.setBlock(xCoord, yCoord, zCoord - 1, Block.getBlockFromItem(ThaumcraftReference.fluxGas.getItem()), ThaumcraftReference.fluxGas.getItemDamage(), 3);
                }

                ticks = 0;
                crafting = false;
                vis = new AspectList();
                recipe = null;
                player = null;
                runningAdjustments = false;
                runningAdjustmentsTicks = 0;
                runningAdjustmentsCount = 0;
                update();
                worldObj.playSoundEffect((double) xCoord, (double) yCoord, (double) zCoord, Sounds.THAUMCRAFT_CRAFT_FAIL, 1.0F, 0.6F);
                worldObj.createExplosion(null, (double) (float) xCoord, (double) ((float) yCoord + 0.5F), (double) ((float) zCoord), 2.0F, false);
                logEvent("Crafting stopped");
                return TRUE;
            }

            if (setEnvironment())
            {
                logEvent("Correct environment found");
                PrimalRecipe pr = AtomaticApi.getPrimalRecipe(inventory[SLOT_INVENTORY_INDEX], getPrimalObject());

                // TODO Think about adding primal reversion research as minimum required research
                if (pr != null && (pr.getResearch() == null || pr.getResearch().equals("") || ThaumcraftApiHelper.isResearchComplete(entityPlayer.getCommandSenderName(), pr.getResearch())))
                {
                    boolean lessVis = false;
                    int lessVisAmount = 0;

                    boolean lessWandVis = false;
                    int lessWandVisAmount = 0;

                    boolean noWandVis = false;

                    boolean lessWarp = false;
                    int lessWarpAmount = 0;

                    boolean noWarp = false;

                    if (getAdjustments() != null)
                    {
                        for (Map.Entry<ChunkCoordinates, Adjustment> entry : getAdjustments().entrySet())
                        {
                            ChunkCoordinates coordinates = entry.getKey();
                            Adjustment adjustment = entry.getValue();

                            if (AdjustmentHelper.isRuntimeAdjustment(adjustment))
                            {
                                continue;
                            }

                            boolean found = false;

                            if (adjustment.effect == AdjustEffect.LESS_VIS)
                            {
                                lessVis = true;
                                lessVisAmount = adjustment.strength;
                                found = true;
                            }
                            else if (adjustment.effect == AdjustEffect.LESS_WAND_VIS)
                            {
                                lessWandVis = true;
                                lessWandVisAmount = adjustment.strength;
                                found = true;
                            }
                            else if (adjustment.effect == AdjustEffect.NO_WAND_VIS)
                            {
                                noWandVis = true;
                                found = true;
                            }
                            else if (adjustment.effect == AdjustEffect.LESS_WARP)
                            {
                                lessWarp = true;
                                lessWarpAmount = adjustment.strength;
                                found = true;
                            }
                            else if (adjustment.effect == AdjustEffect.NO_WARP)
                            {
                                noWarp = true;
                                found = true;
                            }

                            if (found)
                            {
                                if (isPedestal(coordinates.posX, coordinates.posY, coordinates.posZ))
                                {
                                    getPedestalInventory(coordinates.posX, coordinates.posY, coordinates.posZ).setInventorySlotContents(PEDESTAL_SLOT, null);
                                    worldObj.markBlockForUpdate(coordinates.posX, coordinates.posY, coordinates.posZ);
                                    getPedestal(coordinates.posX, coordinates.posY, coordinates.posZ).markDirty();
                                }
                            }
                        }
                    }

                    AspectList startingAspects = pr.getStartingAspectsForWand().copy();

                    if (lessWandVis)
                    {
                        AspectList recipeAspects = pr.getStartingAspectsForWand().copy();
                        startingAspects = new AspectList();

                        for (int i = 0; i < recipeAspects.size(); i++)
                        {
                            Aspect aspect = recipeAspects.getAspectsSorted()[i];
                            startingAspects.add(aspect, recipeAspects.getAmount(aspect) - (lessWandVisAmount * PrimalRecipe.WAND_MULTIPLIER));
                        }
                    }

                    if (noWandVis || ThaumcraftApiHelper.consumeVisFromWand(wandstack, entityPlayer, startingAspects, true, false))
                    {
                        if (noWarp || AtomaticApi.getPrimalRecipeWarp(pr) > 0)
                        {
                            int warpAmount = AtomaticApi.getPrimalRecipeWarp(pr);

                            if (lessWarp)
                            {
                                warpAmount = Math.max(0, warpAmount - lessWarpAmount);
                            }

                            ThaumcraftApiHelper.addStickyWarpToPlayer(entityPlayer, warpAmount);

                            if (world.rand.nextInt(50) >= (AtomaticApi.getPrimalRecipeWarp(pr) + (lessWarpAmount * 3)))
                            {
                                int tempWarpAmount = Math.min(3, Math.round((float) AtomaticApi.getPrimalRecipeWarp(pr) / 2));

                                if (lessWarp)
                                {
                                    tempWarpAmount = Math.max(0, tempWarpAmount - lessWarpAmount);
                                }

                                ThaumcraftApiHelper.addWarpToPlayer(entityPlayer, tempWarpAmount, true);
                            }
                        }

                        worldObj.playSoundEffect((double) xCoord, (double) yCoord, (double) zCoord, Sounds.THAUMCRAFT_CRAFT_START, 0.7F, 1.0F);
                        recipe = pr;
                        crafting = true;
                        AspectList tempVis = recipe.getAspects().copy();

                        if (lessVis)
                        {
                            tempVis = new AspectList();

                            for (int i = 0; i < recipe.getAspects().size(); i++)
                            {
                                Aspect aspect = recipe.getAspects().getAspectsSorted()[i];
                                tempVis.add(aspect, recipe.getAspects().getAmount(aspect) - lessVisAmount);
                            }
                        }

                        vis = tempVis;
                        player = entityPlayer;
                        runningAdjustments = false;
                        runningAdjustmentsTicks = 0;
                        runningAdjustmentsCount = 0;
                        update();
                        logEvent("Crafting started");
                        return TRUE;
                    }
                }
            }

            worldObj.playSoundEffect((double) xCoord, (double) yCoord, (double) zCoord, Sounds.THAUMCRAFT_CRAFT_FAIL, 1.0F, 0.6F);

            logEvent("No recipe found");
        }

        return FALSE;
    }

    @Override
    public ItemStack onWandRightClick(World world, ItemStack wandstack, EntityPlayer player)
    {
        // I have no idea what I should use this for
        return null;
    }

    @Override
    public void onUsingWandTick(ItemStack wandstack, EntityPlayer player, int count)
    {
        // NO-OP
    }

    @Override
    public void onWandStoppedUsing(ItemStack wandstack, World world, EntityPlayer player, int count)
    {
        // NO-OP
    }

    @Override
    public AspectList getAspects()
    {
        return vis;
    }

    @Override
    public void setAspects(AspectList aspects)
    {
        // NO-OP
    }

    @Override
    public boolean doesContainerAccept(Aspect tag)
    {
        return true;
    }

    @Override
    public int addToContainer(Aspect tag, int amount)
    {
        return 0;
    }

    @Override
    public boolean takeFromContainer(Aspect tag, int amount)
    {
        return false;
    }

    @Override
    public boolean takeFromContainer(AspectList ot)
    {
        return false;
    }

    @Override
    public boolean doesContainerContainAmount(Aspect tag, int amount)
    {
        return false;
    }

    @Override
    public boolean doesContainerContain(AspectList ot)
    {
        return false;
    }

    @Override
    public int containerContains(Aspect tag)
    {
        return 0;
    }

    public boolean isCrafting()
    {
        return crafting;
    }

    public boolean checkEnvironment()
    {
        for (ChunkCoordinates coordinates : pedestals)
        {
            if (!isPedestal(coordinates.posX, coordinates.posY, coordinates.posZ))
            {
                return false;
            }
        }

        for (ChunkCoordinates coordinates : crystals)
        {
            if (!isCrystal(coordinates.posX, coordinates.posY, coordinates.posZ))
            {
                return false;
            }
        }

        return getPrimalPedestal() != null && doesPrimalPedestalHavePrimalObject();
    }

    public Map<ChunkCoordinates, Adjustment> getAdjustments()
    {
        Map<ChunkCoordinates, Adjustment> adjustments = null;

        for (ChunkCoordinates coordinates : pedestals)
        {
            if (coordinates.equals(primalPedestal))
            {
                continue;
            }

            IInventory pedestal = getPedestalInventory(coordinates.posX, coordinates.posY, coordinates.posZ);

            if (AtomaticApi.getPrimalCraftingAdjuster(pedestal.getStackInSlot(PEDESTAL_SLOT)) != null)
            {
                if (adjustments == null)
                {
                    adjustments = new HashMap<ChunkCoordinates, Adjustment>();
                }

                adjustments.put(coordinates, AtomaticApi.getPrimalCraftingAdjuster(pedestal.getStackInSlot(PEDESTAL_SLOT)));
            }
        }

        for (ChunkCoordinates coordinates : crystals)
        {
            if (!noAdjustmentAllowed.contains(coordinates))
            {
                if (getICrystal(coordinates.posX, coordinates.posY, coordinates.posZ) != null)
                {
                    if (getICrystal(coordinates.posX, coordinates.posY, coordinates.posZ).isAdjustment())
                    {
                        if (adjustments == null)
                        {
                            adjustments = new HashMap<ChunkCoordinates, Adjustment>();
                        }

                        adjustments.put(coordinates, getICrystal(coordinates.posX, coordinates.posY, coordinates.posZ).getAdjustment());
                    }
                }
            }
        }

        if (worldObj.blockExists(xCoord, yCoord - 1, zCoord))
        {
            if (AtomaticApi.getPrimalCraftingAdjuster(new ItemStack(worldObj.getBlock(xCoord, yCoord - 1, zCoord), 1, worldObj.getBlockMetadata(xCoord, yCoord - 1, zCoord))) != null)
            {
                if (adjustments == null)
                {
                    adjustments = new HashMap<ChunkCoordinates, Adjustment>();
                }

                adjustments.put(new ChunkCoordinates(xCoord, yCoord - 1, zCoord), AtomaticApi.getPrimalCraftingAdjuster(new ItemStack(worldObj.getBlock(xCoord, yCoord - 1, zCoord), 1, worldObj.getBlockMetadata(xCoord, yCoord - 1, zCoord))));
            }
        }

        return adjustments;
    }

    public Map<ChunkCoordinates, Adjustment> getRuntimeAdjustments()
    {
        if (getAdjustments() == null)
        {
            return null;
        }

        Map<ChunkCoordinates, Adjustment> adjustmentMap = null;

        for (Map.Entry<ChunkCoordinates, Adjustment> entry : getAdjustments().entrySet())
        {
            if (AdjustmentHelper.isRuntimeAdjustment(entry.getValue()))
            {
                if (adjustmentMap == null)
                {
                    adjustmentMap = new HashMap<ChunkCoordinates, Adjustment>();
                }

                adjustmentMap.put(entry.getKey(), entry.getValue());
            }
        }

        return adjustmentMap;
    }

    public boolean setEnvironment()
    {
        logEvent("Setting environment");

        pedestals.clear();
        crystals.clear();

        if (isPedestal(xCoord + PEDESTAL_OFFSET, yCoord, zCoord) && isCrystal(xCoord + CRYSTAL_OFFSET, yCoord, zCoord))
        {
            pedestals.add(new ChunkCoordinates(xCoord + PEDESTAL_OFFSET, yCoord, zCoord));
            crystals.add(new ChunkCoordinates(xCoord + CRYSTAL_OFFSET, yCoord, zCoord));

            if (primalPedestal == null && hasPrimalObject(xCoord + PEDESTAL_OFFSET, yCoord, zCoord))
            {
                primalPedestal = new ChunkCoordinates(xCoord + PEDESTAL_OFFSET, yCoord, zCoord);
            }

            if (isRelay(xCoord + PEDESTAL_OFFSET, yCoord + PEDESTAL_RELAY_OFFSET, zCoord))
            {
                crystals.add(new ChunkCoordinates(xCoord + PEDESTAL_OFFSET, yCoord + PEDESTAL_RELAY_OFFSET, zCoord));
            }
        }

        if (isPedestal(xCoord - PEDESTAL_OFFSET, yCoord, zCoord) && isCrystal(xCoord - CRYSTAL_OFFSET, yCoord, zCoord))
        {
            pedestals.add(new ChunkCoordinates(xCoord - PEDESTAL_OFFSET, yCoord, zCoord));
            crystals.add(new ChunkCoordinates(xCoord - CRYSTAL_OFFSET, yCoord, zCoord));

            if (primalPedestal == null && hasPrimalObject(xCoord - PEDESTAL_OFFSET, yCoord, zCoord))
            {
                primalPedestal = new ChunkCoordinates(xCoord - PEDESTAL_OFFSET, yCoord, zCoord);
            }

            if (isRelay(xCoord - PEDESTAL_OFFSET, yCoord + PEDESTAL_RELAY_OFFSET, zCoord))
            {
                crystals.add(new ChunkCoordinates(xCoord - PEDESTAL_OFFSET, yCoord + PEDESTAL_RELAY_OFFSET, zCoord));
            }
        }

        if (isPedestal(xCoord, yCoord, zCoord + PEDESTAL_OFFSET) && isCrystal(xCoord, yCoord, zCoord + CRYSTAL_OFFSET))
        {
            pedestals.add(new ChunkCoordinates(xCoord, yCoord, zCoord + PEDESTAL_OFFSET));
            crystals.add(new ChunkCoordinates(xCoord, yCoord, zCoord + CRYSTAL_OFFSET));

            if (primalPedestal == null && hasPrimalObject(xCoord, yCoord, zCoord + PEDESTAL_OFFSET))
            {
                primalPedestal = new ChunkCoordinates(xCoord, yCoord, zCoord + PEDESTAL_OFFSET);
            }

            if (isRelay(xCoord, yCoord + PEDESTAL_RELAY_OFFSET, zCoord + PEDESTAL_OFFSET))
            {
                crystals.add(new ChunkCoordinates(xCoord, yCoord + PEDESTAL_RELAY_OFFSET, zCoord + PEDESTAL_OFFSET));
            }
        }

        if (isPedestal(xCoord, yCoord, zCoord - PEDESTAL_OFFSET) && isCrystal(xCoord, yCoord, zCoord - CRYSTAL_OFFSET))
        {
            pedestals.add(new ChunkCoordinates(xCoord, yCoord, zCoord - PEDESTAL_OFFSET));
            crystals.add(new ChunkCoordinates(xCoord, yCoord, zCoord - CRYSTAL_OFFSET));

            if (primalPedestal == null && hasPrimalObject(xCoord, yCoord, zCoord - PEDESTAL_OFFSET))
            {
                primalPedestal = new ChunkCoordinates(xCoord, yCoord, zCoord - PEDESTAL_OFFSET);
            }

            if (isRelay(xCoord, yCoord + PEDESTAL_RELAY_OFFSET, zCoord - PEDESTAL_OFFSET))
            {
                crystals.add(new ChunkCoordinates(xCoord, yCoord + PEDESTAL_RELAY_OFFSET, zCoord - PEDESTAL_OFFSET));
            }
        }

        if (isCrystal(xCoord + CLOSE_CRYSTAL_OFFSET, yCoord, zCoord))
        {
            crystals.add(new ChunkCoordinates(xCoord + CLOSE_CRYSTAL_OFFSET, yCoord, zCoord));
        }

        if (isCrystal(xCoord - CLOSE_CRYSTAL_OFFSET, yCoord, zCoord))
        {
            crystals.add(new ChunkCoordinates(xCoord - CLOSE_CRYSTAL_OFFSET, yCoord, zCoord));
        }

        if (isCrystal(xCoord, yCoord, zCoord + CLOSE_CRYSTAL_OFFSET))
        {
            crystals.add(new ChunkCoordinates(xCoord, yCoord, zCoord + CLOSE_CRYSTAL_OFFSET));
        }

        if (isCrystal(xCoord, yCoord, zCoord - CLOSE_CRYSTAL_OFFSET))
        {
            crystals.add(new ChunkCoordinates(xCoord, yCoord, zCoord - CLOSE_CRYSTAL_OFFSET));
        }

        return primalPedestal != null && hasAltarRelay();
    }

    public boolean canCraft()
    {
        return checkEnvironment() && AtomaticApi.getPrimalRecipe(inventory[SLOT_INVENTORY_INDEX], getPrimalObject()) != null && recipe != null && recipe.equals(AtomaticApi.getPrimalRecipe(inventory[SLOT_INVENTORY_INDEX], getPrimalObject()));
    }

    public boolean isPedestal(int x, int y, int z)
    {
        return worldObj.blockExists(x, y, z) && Item.getItemFromBlock(worldObj.getBlock(x, y, z)) == ThaumcraftReference.arcanePedestal.getItem() && worldObj.getBlockMetadata(x, y, z) == ThaumcraftReference.arcanePedestal.getItemDamage() && worldObj.getTileEntity(x, y, z) instanceof ISidedInventory && worldObj.getTileEntity(x, y, z) instanceof TileThaumcraft;
    }

    public boolean isPedestalInventoryEmpty(int x, int y, int z)
    {
        return getPedestalInventory(x, y, z) == null || (getPedestalInventory(x, y, z).getStackInSlot(PEDESTAL_SLOT) == null || (getPedestalInventory(x, y, z).getStackInSlot(PEDESTAL_SLOT).stackSize <= 0));
    }

    public boolean hasPrimalObject(int x, int y, int z)
    {
        return getPedestalStack(x, y, z) != null && getPedestalStack(x, y, z).getItem() == ModItems.primalObject;
    }

    /**
     * AKA Lazy me
     */
    public boolean doesPrimalPedestalHavePrimalObject()
    {
        return getPrimalPedestalStack() != null && getPrimalPedestalStack().getItem() == ModItems.primalObject;
    }

    public TileThaumcraft getPedestal(int x, int y, int z)
    {
        return isPedestal(x, y, z) ? (TileThaumcraft) worldObj.getTileEntity(x, y, z) : null;
    }

    public IInventory getPedestalInventory(int x, int y, int z)
    {
        return getPedestal(x, y, z) == null ? null : (IInventory) getPedestal(x, y, z);
    }

    public ItemStack getPedestalStack(int x, int y, int z)
    {
        return isPedestalInventoryEmpty(x, y, z) ? null : getPedestalInventory(x, y, z).getStackInSlot(PEDESTAL_SLOT);
    }

    public TileThaumcraft getPrimalPedestal()
    {
        return getPedestal(primalPedestal.posX, primalPedestal.posY, primalPedestal.posZ);
    }

    public IInventory getPrimalPedestalInventory()
    {
        return getPrimalPedestal() == null ? null : (IInventory) getPrimalPedestal();
    }

    public ItemStack getPrimalPedestalStack()
    {
        return isPedestalInventoryEmpty(primalPedestal.posX, primalPedestal.posY, primalPedestal.posZ) ? null : getPrimalPedestalInventory().getStackInSlot(PEDESTAL_SLOT);
    }

    public PrimalObject getPrimalObject()
    {
        return getPrimalPedestalInventory() == null ? null : ItemPrimalObject.getPrimalObject(getPrimalPedestalInventory().getStackInSlot(PEDESTAL_SLOT));
    }

    public boolean isCrystal(int x, int y, int z)
    {
        return worldObj.blockExists(x, y, z) && worldObj.getTileEntity(x, y, z) instanceof ICrystal;
    }

    public TileEntity getCrystal(int x, int y, int z)
    {
        return isCrystal(x, y, z) ? worldObj.getTileEntity(x, y, z) : null;
    }

    public ICrystal getICrystal(int x, int y, int z)
    {
        return getCrystal(x, y, z) != null && getCrystal(x, y, z) instanceof ICrystal ? (ICrystal) worldObj.getTileEntity(x, y, z) : null;
    }

    public boolean isRelay(int x, int y, int z)
    {
        return worldObj.blockExists(x, y, z) && worldObj.getTileEntity(x, y, z) instanceof IRelay;
    }

    public TileEntity getRelay(int x, int y, int z)
    {
        return isRelay(x, y, z) ? worldObj.getTileEntity(x, y, z) : null;
    }

    public IRelay getIRelay(int x, int y, int z)
    {
        return getRelay(x, y, z) != null && getRelay(x, y, z) instanceof IRelay ? (IRelay) worldObj.getTileEntity(x, y, z) : null;
    }

    public boolean hasAltarRelay()
    {
        return worldObj.blockExists(xCoord, yCoord + ALTAR_RELAY_OFFSET, zCoord) && worldObj.getTileEntity(xCoord, yCoord + ALTAR_RELAY_OFFSET, zCoord) instanceof IAltarRelay;
    }

    public TileEntity getAltarRelay()
    {
        return hasAltarRelay() ? worldObj.getTileEntity(xCoord, yCoord + ALTAR_RELAY_OFFSET, zCoord) : null;
    }

    public IAltarRelay getIAltarRelay(int x, int y, int z)
    {
        return getAltarRelay() != null && getAltarRelay() instanceof IAltarRelay ? (IAltarRelay) worldObj.getTileEntity(xCoord, yCoord + ALTAR_RELAY_OFFSET, zCoord) : null;
    }

    public void dropItemsAtEntity(Entity entity)
    {
        InventoryHelper.dropItemsAtEntity(worldObj, xCoord, yCoord, zCoord, entity);
        update();
    }

    private void markThisForUpdate()
    {
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private void update()
    {
        markThisForUpdate();
        markDirty();
    }

    private void logEvent(Object object)
    {
        LogHelper.logAltarEvent(this, object);
    }
}
