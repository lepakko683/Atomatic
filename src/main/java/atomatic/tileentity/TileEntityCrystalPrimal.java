package atomatic.tileentity;

import atomatic.api.AtomaticApi;
import atomatic.api.primal.PrimalObject;
import atomatic.api.primal.PrimalRecipe;

import atomatic.Atomatic;
import atomatic.item.ItemPrimalObject;
import atomatic.reference.Names;
import atomatic.reference.ThaumcraftReference;
import atomatic.util.InputDirection;
import atomatic.util.LogHelper;
import atomatic.util.NBTHelper;

import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.visnet.VisNetHandler;
import thaumcraft.api.wands.IWandable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import net.minecraftforge.common.util.ForgeDirection;

// TODO Own special pedestal type for the primal object (maybe?)
// TODO Explode if crafting is interrupted
// TODO Some fancy particles during the crafting
// TODO Start crafting only with wand
public class TileEntityCrystalPrimal extends TileEntityA implements IWandable
{
    public static final int PEDESTAL_OFFSET = 2;
    public static final String X_AXIS = "x";
    public static final String Z_AXIS = "z";
    public static final int PEDESTAL_SLOT = 0;
    public static final int MAX_VIS_DRAIN = 20;
    public static final int FREQUENCY = 5;

    private static final int TRUE = 1;
    private static final int FALSE = -TRUE;

    protected int ticks = 0;
    protected boolean wanded = false;
    protected boolean crafting = false;
    protected AspectList vis = new AspectList();
    protected PrimalRecipe recipe = null;

    @Override
    public void readFromNBT(NBTTagCompound nbtTagCompound)
    {
        super.readFromNBT(nbtTagCompound);
        readNBT(nbtTagCompound);
    }

    @Override
    protected void readNBT(NBTTagCompound nbtTagCompound)
    {
        ticks = nbtTagCompound.getInteger(Names.NBT.TICKS);
        wanded = nbtTagCompound.getBoolean(Names.NBT.WANDED);
        crafting = nbtTagCompound.getBoolean(Names.NBT.CRAFTING);
        vis.readFromNBT(nbtTagCompound, Names.NBT.VIS);

        int recipeHash = nbtTagCompound.getInteger(Names.NBT.RECIPE);

        if (recipeHash == 0)
        {
            recipe = null;
        }
        else
        {
            recipe = AtomaticApi.getPrimalRecipeForHash(recipeHash);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbtTagCompound)
    {
        super.writeToNBT(nbtTagCompound);
        writeNBT(nbtTagCompound);
    }

    @Override
    protected void writeNBT(NBTTagCompound nbtTagCompound)
    {
        nbtTagCompound.setInteger(Names.NBT.TICKS, ticks);
        nbtTagCompound.setBoolean(Names.NBT.WANDED, wanded);
        nbtTagCompound.setBoolean(Names.NBT.CRAFTING, crafting);
        vis.writeToNBT(nbtTagCompound, Names.NBT.VIS);
        nbtTagCompound.setInteger(Names.NBT.RECIPE, recipe == null ? 0 : recipe.hashCode());
    }

    @Override
    public void updateEntity()
    {
        boolean needsUpdate = false;

        if (crafting)
        {
            ++ticks;
            LogHelper.debug("Ticking (" + toString() + ")");
        }

        if (!this.worldObj.isRemote)
        {
            if (wanded && canCraft())
            {
                needsUpdate = true;
                crafting = true;
                vis = recipe.getAspects();
            }

            if (crafting && canCraft())
            {
                if (ticks % FREQUENCY == 0)
                {
                    LogHelper.debug("Attempting to drain vis (" + toString() + ")");
                    Aspect aspect = vis.getAspectsSortedAmount()[vis.getAspectsSortedAmount().length - 1];
                    int visDrain = VisNetHandler.drainVis(worldObj, xCoord, yCoord, zCoord, aspect, Math.min(MAX_VIS_DRAIN, vis.getAmount(aspect)));

                    if (visDrain > 0)
                    {
                        LogHelper.debug("Drained " + visDrain + " " + aspect.getTag() + " vis (" + toString() + ")");
                        vis.remove(aspect, visDrain);
                        needsUpdate = true;
                    }
                }

                if (vis.visSize() <= 0 && ticks >= recipe.getTime())
                {
                    getPrimalPedestal().decrStackSize(PEDESTAL_SLOT, 1); // TODO NPE? getPrimalPedestal().setInventorySlotContents(PEDESTAL_SLOT, null);
                    getInputPedestal().setInventorySlotContents(PEDESTAL_SLOT, new ItemStack(recipe.getOutput().getItem(), 1, recipe.getOutput().getItemDamage()));
                    crafting = false;
                    needsUpdate = true;
                }
            }

            if (crafting && !canCraft())
            {
                ticks = 0;
                wanded = false;
                crafting = false;
                vis = new AspectList();
                recipe = null;
                needsUpdate = true;
            }
        }

        if (needsUpdate)
        {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            markDirty();
        }
    }

    @Override
    public int onWandRightClick(World world, ItemStack wandstack, EntityPlayer player, int x, int y, int z, int side, int md)
    {
        if (!worldObj.isRemote)
        {
            LogHelper.debug("Wanded (" + toString() + ")");

            if (recipe != null)
            {
                if (recipe.equals(AtomaticApi.getPrimalRecipe(getInputStack(), getPrimalObject())))
                {
                    return FALSE;
                }
            }

            PrimalRecipe pr = AtomaticApi.getPrimalRecipe(getInputStack(), getPrimalObject());

            if (pr != null && (pr.getResearch() == null || pr.getResearch().equals("") || ThaumcraftApiHelper.isResearchComplete(player.getCommandSenderName(), pr.getResearch())))
            {
                recipe = pr;
                ThaumcraftApiHelper.consumeVisFromWand(wandstack, player, new AspectList().add(Aspect.AIR, 1).add(Aspect.FIRE, 1).add(Aspect.WATER, 1).add(Aspect.EARTH, 1).add(Aspect.ORDER, 1).add(Aspect.ENTROPY, 1), true, false);
                wanded = true;
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                markDirty();
                return TRUE;
            }

            LogHelper.debug("No recipe found (" + toString() + ")");
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

    protected InputDirection inputDirection()
    {
        InputDirection direction = null;

        if (pedestalAxis() != null && !pedestalAxis().equals(""))
        {
            if (pedestalAxis().equals(X_AXIS) && getPedestal(xCoord + PEDESTAL_OFFSET, yCoord, zCoord) != null && getPedestal(xCoord - PEDESTAL_OFFSET, yCoord, zCoord) != null)
            {
                if (AtomaticApi.primalRecipeExists(getPedestal(xCoord + PEDESTAL_OFFSET, yCoord, zCoord).getStackInSlot(PEDESTAL_SLOT), ItemPrimalObject.getPrimalObject(getPedestal(xCoord - PEDESTAL_OFFSET, yCoord, zCoord).getStackInSlot(PEDESTAL_SLOT))))
                {
                    direction = InputDirection.POSITIVE;
                }
                else if (AtomaticApi.primalRecipeExists(getPedestal(xCoord - PEDESTAL_OFFSET, yCoord, zCoord).getStackInSlot(PEDESTAL_SLOT), ItemPrimalObject.getPrimalObject(getPedestal(xCoord + PEDESTAL_OFFSET, yCoord, zCoord).getStackInSlot(PEDESTAL_SLOT))))
                {
                    direction = InputDirection.NEGATIVE;
                }
            }
            else if (pedestalAxis().equals(Z_AXIS) && getPedestal(xCoord, yCoord, zCoord + PEDESTAL_OFFSET) != null && getPedestal(xCoord, yCoord, zCoord - PEDESTAL_OFFSET) != null)
            {
                if (AtomaticApi.primalRecipeExists(getPedestal(xCoord, yCoord, zCoord + PEDESTAL_OFFSET).getStackInSlot(PEDESTAL_SLOT), ItemPrimalObject.getPrimalObject(getPedestal(xCoord, yCoord, zCoord - PEDESTAL_OFFSET).getStackInSlot(PEDESTAL_SLOT))))
                {
                    direction = InputDirection.POSITIVE;
                }
                else if (AtomaticApi.primalRecipeExists(getPedestal(xCoord, yCoord, zCoord - PEDESTAL_OFFSET).getStackInSlot(PEDESTAL_SLOT), ItemPrimalObject.getPrimalObject(getPedestal(xCoord, yCoord, zCoord + PEDESTAL_OFFSET).getStackInSlot(PEDESTAL_SLOT))))
                {
                    direction = InputDirection.NEGATIVE;
                }
            }
        }

        return direction;
    }

    protected boolean isPedestal(int x, int y, int z)
    {
        return worldObj.blockExists(x, y, z) && Item.getItemFromBlock(worldObj.getBlock(x, y, z)) == ThaumcraftReference.arcanePedestal.getItem() && worldObj.getBlockMetadata(x, y, z) == ThaumcraftReference.arcanePedestal.getItemDamage() && worldObj.getTileEntity(x, y, z) instanceof ISidedInventory;
    }

    protected ISidedInventory getPedestal(int x, int y, int z)
    {
        return isPedestal(x, y, z) ? (ISidedInventory) worldObj.getTileEntity(x, y, z) : null;
    }

    protected ISidedInventory getInputPedestal()
    {
        if (inputDirection() == InputDirection.NEGATIVE)
        {
            if (pedestalAxis().equals(X_AXIS))
            {
                return getPedestal(xCoord - PEDESTAL_OFFSET, yCoord, zCoord);
            }
            else if (pedestalAxis().equals(Z_AXIS))
            {
                return getPedestal(xCoord, yCoord, zCoord - PEDESTAL_OFFSET);
            }
        }
        else if (inputDirection() == InputDirection.POSITIVE)
        {
            if (pedestalAxis().equals(X_AXIS))
            {
                return getPedestal(xCoord + PEDESTAL_OFFSET, yCoord, zCoord);
            }
            else if (pedestalAxis().equals(Z_AXIS))
            {
                return getPedestal(xCoord, yCoord, zCoord + PEDESTAL_OFFSET);
            }
        }

        return null;
    }

    protected ISidedInventory getPrimalPedestal()
    {
        if (inputDirection() == InputDirection.NEGATIVE)
        {
            if (pedestalAxis().equals(X_AXIS))
            {
                return getPedestal(xCoord + PEDESTAL_OFFSET, yCoord, zCoord);
            }
            else if (pedestalAxis().equals(Z_AXIS))
            {
                return getPedestal(xCoord, yCoord, zCoord + PEDESTAL_OFFSET);
            }
        }
        else if (inputDirection() == InputDirection.POSITIVE)
        {
            if (pedestalAxis().equals(X_AXIS))
            {
                return getPedestal(xCoord - PEDESTAL_OFFSET, yCoord, zCoord);
            }
            else if (pedestalAxis().equals(Z_AXIS))
            {
                return getPedestal(xCoord, yCoord, zCoord - PEDESTAL_OFFSET);
            }
        }

        return null;
    }

    protected ItemStack getInputStack()
    {
        if (getInputPedestal() != null)
        {
            return getInputPedestal().getStackInSlot(PEDESTAL_SLOT);
        }

        return null;
    }

    protected ItemStack getPrimalStack()
    {
        if (getPrimalPedestal() != null)
        {
            return getPrimalPedestal().getStackInSlot(PEDESTAL_SLOT);
        }

        return null;
    }

    protected PrimalObject getPrimalObject()
    {
        if (getPrimalStack() != null)
        {
            return ItemPrimalObject.getPrimalObject(getPrimalStack());
        }

        return null;
    }

    protected String pedestalAxis()
    {
        String axis = "";

        if (isPedestal(xCoord + PEDESTAL_OFFSET, yCoord, zCoord) && isPedestal(xCoord - PEDESTAL_OFFSET, yCoord, zCoord))
        {
            axis = X_AXIS;
        }
        else if (isPedestal(xCoord, yCoord, zCoord + PEDESTAL_OFFSET) && isPedestal(xCoord, yCoord, zCoord - PEDESTAL_OFFSET))
        {
            axis = Z_AXIS;
        }

        return axis;
    }

    protected boolean canCraft()
    {
        return inputDirection() != null && AtomaticApi.getPrimalRecipe(getInputStack(), getPrimalObject()) != null && recipe != null && recipe.equals(AtomaticApi.getPrimalRecipe(getInputStack(), getPrimalObject()));
    }
}
