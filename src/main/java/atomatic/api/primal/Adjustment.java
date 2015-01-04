package atomatic.api.primal;

public class Adjustment
{
    public final AdjustEffect effect;
    public final int strength;

    public Adjustment(AdjustEffect effect, int strength)
    {
        this.effect = effect;
        this.strength = strength;
    }
}