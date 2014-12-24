package atomatic.item;

import atomatic.reference.Names;
import atomatic.reference.Textures;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.util.List;

public class ItemPrimalObject extends ItemA
{
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public ItemPrimalObject()
    {
        super();
        this.setMaxStackSize(64);
        this.setHasSubtypes(true);
        this.setUnlocalizedName(Names.Items.PRIMAL_OBJECT);
    }

    @Override
    public String getUnlocalizedName()
    {
        return String.format("item.%s%s", Textures.RESOURCE_PREFIX, Names.Items.PRIMAL_OBJECT);
    }

    @Override
    public String getUnlocalizedName(ItemStack itemStack)
    {
        return String.format("item.%s%s.%s", Textures.RESOURCE_PREFIX, Names.Items.PRIMAL_OBJECT, Names.Items.PRIMAL_OBJECT_SUBTYPES[MathHelper.clamp_int(itemStack.getItemDamage(), 0, Names.Items.PRIMAL_OBJECT_SUBTYPES.length - 1)]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void getSubItems(Item item, CreativeTabs creativeTab, List list)
    {
        for (int meta = 0; meta < Names.Items.PRIMAL_OBJECT_SUBTYPES.length; ++meta)
        {
            list.add(new ItemStack(this, 1, meta));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta)
    {
        return icons[MathHelper.clamp_int(meta, 0, Names.Items.PRIMAL_OBJECT_SUBTYPES.length - 1)];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister)
    {
        icons = new IIcon[Names.Items.PRIMAL_OBJECT_SUBTYPES.length];

        for (int i = 0; i < Names.Items.PRIMAL_OBJECT_SUBTYPES.length; i++)
        {
            icons[i] = iconRegister.registerIcon(Textures.RESOURCE_PREFIX + Names.Items.PRIMAL_OBJECT + "." + Names.Items.PRIMAL_OBJECT_SUBTYPES[i]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemStack, EntityPlayer entityPlayer, List list, boolean flag)
    {

    }
}