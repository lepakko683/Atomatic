package atomatic.tileentity;

import atomatic.api.adjusting.Adjustment;
import atomatic.api.ICrystal;

public class TileEntityCrystalPrimal extends TileEntityA implements ICrystal
{
    @Override
    public boolean isPrimal()
    {
        return true;
    }

    @Override
    public boolean isAdjustment()
    {
        return false;
    }

    @Override
    public Adjustment getAdjustment()
    {
        return null;
    }
}
