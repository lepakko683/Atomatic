package atomatic.api;

import atomatic.api.primal.PrimalObject;
import atomatic.api.primal.PrimalRecipe;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class AtomaticApi
{
    /**
     * All {@link PrimalRecipe}s.
     */
    private static List<PrimalRecipe> primalRecipes = new ArrayList<PrimalRecipe>();

    /**
     * Adds a new {@link PrimalRecipe}.
     *
     * @param research the research key required for this recipe to work.
     * @param output   the recipe's output.
     * @param primal   the {@link PrimalObject} used to craft this.
     * @param input    the recipe's input.
     *
     * @return the created {@link PrimalRecipe}.
     */
    public static PrimalRecipe addPrimalRecipe(String research, ItemStack output, PrimalObject primal, ItemStack input)
    {
        return addPrimalRecipe(research, output, 0, primal, input);
    }

    /**
     * Adds a new {@link PrimalRecipe}.
     *
     * @param research the research key required for this recipe to work.
     * @param output   the recipe's output.
     * @param time     the time this recipe takes to make in ticks.
     * @param primal   the {@link PrimalObject} used to craft this.
     * @param input    the recipe's input.
     *
     * @return the created {@link PrimalRecipe}.
     */
    public static PrimalRecipe addPrimalRecipe(String research, ItemStack output, int time, PrimalObject primal, ItemStack input)
    {
        PrimalRecipe recipe = new PrimalRecipe(research, output, time, primal, input);
        primalRecipes.add(recipe);
        return recipe;
    }

    /**
     * Adds a new {@link PrimalRecipe}.
     *
     * @param research the research key required for this recipe to work.
     * @param output   the recipe's output.
     * @param seconds  the time this recipe takes to make in seconds.
     * @param primal   the {@link PrimalObject} used to craft this.
     * @param input    the recipe's input.
     *
     * @return the created {@link PrimalRecipe}.
     */
    public static PrimalRecipe addPrimalRecipeSeconds(String research, ItemStack output, int seconds, PrimalObject primal, ItemStack input)
    {
        return addPrimalRecipe(research, output, seconds * 20, primal, input);
    }

    /**
     * Gives the first matching registered {@link PrimalRecipe} from the for an input {@link ItemStack}.
     *
     * @param input the input {@link ItemStack}.
     *
     * @return the {@link PrimalRecipe}.
     */
    public static PrimalRecipe getPrimalRecipe(ItemStack input)
    {
        return getPrimalRecipe(input, null);
    }

    /**
     * Gives a registered {@link PrimalRecipe} for an input {@link ItemStack} and {@link PrimalObject}.
     *
     * @param input  the input {@link ItemStack}.
     * @param primal the {@link PrimalObject} used in the recipe.
     *
     * @return the {@link PrimalRecipe}.
     */
    public static PrimalRecipe getPrimalRecipe(ItemStack input, PrimalObject primal)
    {
        for (PrimalRecipe recipe : primalRecipes)
        {
            if (primal == null)
            {
                if (recipe.getInput().isItemEqual(input))
                {
                    return recipe;
                }
            }
            else
            {
                if (recipe.getInput().isItemEqual(input) && recipe.getPrimal() == primal)
                {
                    return recipe;
                }
            }
        }

        return null;
    }

    /**
     * Gives a registered {@link PrimalRecipe} for a hash code.
     *
     * @param hash the hash code.
     *
     * @return the {@link PrimalRecipe}.
     */
    public static PrimalRecipe getPrimalRecipeForHash(int hash)
    {
        for (PrimalRecipe recipe : primalRecipes)
        {
            if (recipe.hashCode() == hash)
            {
                return recipe;
            }
        }

        return null;
    }

    /**
     * Checks if a {@link PrimalRecipe} for an input {@link ItemStack} exists.
     *
     * @param input the input {@link ItemStack}.
     *
     * @return {@code true} if the {@link PrimalRecipe} exists, otherwise {@code false}.
     */
    public static boolean primalRecipeExists(ItemStack input)
    {
        return primalRecipeExists(input, null);
    }

    /**
     * Checks if a {@link PrimalRecipe} for an input {@link ItemStack} and {@link PrimalObject} exists.
     *
     * @param input  the input {@link ItemStack}.
     * @param primal the {@link PrimalObject} used in the recipe.
     *
     * @return {@code true} if the {@link PrimalRecipe} exists, otherwise {@code false}.
     */
    public static boolean primalRecipeExists(ItemStack input, PrimalObject primal)
    {
        return getPrimalRecipe(input, primal) != null;
    }
}