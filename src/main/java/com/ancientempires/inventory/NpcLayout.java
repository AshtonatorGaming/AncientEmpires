package com.ancientempires.inventory;

import com.ancientempires.entity.npc.NpcInventory;

/**
 * Gui-pixel layout shared by the menu and the client screen. Slot fields are
 * final, so the menu writes them here. The screen places widgets on the same
 * grid. Vanilla slot coordinates are the 16px item, one pixel inside the cell.
 */
public final class NpcLayout
{
    public static final int WIDTH = 230;
    public static final int HEIGHT = 250;
    public static final int SLOT = 18;
    public static final int PREVIEW_X = 70;
    public static final int PREVIEW_Y = 28;
    public static final int PREVIEW_W = 84;
    public static final int PREVIEW_H = 104;
    public static final int CROSS_X = 8;
    public static final int CROSS_Y = PREVIEW_Y + (PREVIEW_H - SLOT * 4) / 2;
    public static final int BACKPACK_X = PREVIEW_X + PREVIEW_W + 8;
    public static final int BACKPACK_Y = PREVIEW_Y + (PREVIEW_H - SLOT * 3) / 2;
    public static final int NAME_Y = 8;
    public static final int NAME_H = 16;
    public static final int BUTTON_SIZE = 16;
    public static final int BUTTON_GAP = 4;
    public static final int BUTTON_Y = PREVIEW_Y + PREVIEW_H + 6;
    public static final int BUTTON_X = (WIDTH - (5 * BUTTON_SIZE + 4 * BUTTON_GAP)) / 2;
    public static final int PLAYER_X = (WIDTH - 9 * SLOT) / 2;
    public static final int PLAYER_Y = BUTTON_Y + BUTTON_SIZE + 12;
    public static final int HOTBAR_Y = PLAYER_Y + 3 * SLOT + 4;
    public static final int SECTION_LABEL_H = 10;
    public static final int BACKPACK_LABEL_Y = BACKPACK_Y - SECTION_LABEL_H - 1;
    public static final int PLAYER_LABEL_Y = PLAYER_Y - SECTION_LABEL_H - 1;

    private NpcLayout()
    {
    }

    public static int cellX(int index)
    {
        if (index == NpcInventory.MAIN_HAND_SLOT)
        {
            return CROSS_X;
        }
        if (index == NpcInventory.OFFHAND_SLOT)
        {
            return CROSS_X + 2 * SLOT;
        }
        if (index == NpcInventory.HEAD_SLOT || index == NpcInventory.CHEST_SLOT
                || index == NpcInventory.LEGS_SLOT || index == NpcInventory.FEET_SLOT)
        {
            return CROSS_X + SLOT;
        }
        if (index < NpcInventory.SLOT_COUNT)
        {
            return BACKPACK_X + (index - NpcInventory.BACKPACK_START) % 3 * SLOT;
        }
        int player = index - NpcInventory.SLOT_COUNT;
        if (player < 27)
        {
            return PLAYER_X + player % 9 * SLOT;
        }
        return PLAYER_X + (player - 27) * SLOT;
    }

    public static int cellY(int index)
    {
        if (index == NpcInventory.HEAD_SLOT)
        {
            return CROSS_Y;
        }
        if (index == NpcInventory.MAIN_HAND_SLOT || index == NpcInventory.CHEST_SLOT || index == NpcInventory.OFFHAND_SLOT)
        {
            return CROSS_Y + SLOT;
        }
        if (index == NpcInventory.LEGS_SLOT)
        {
            return CROSS_Y + 2 * SLOT;
        }
        if (index == NpcInventory.FEET_SLOT)
        {
            return CROSS_Y + 3 * SLOT;
        }
        if (index < NpcInventory.SLOT_COUNT)
        {
            return BACKPACK_Y + (index - NpcInventory.BACKPACK_START) / 3 * SLOT;
        }
        int player = index - NpcInventory.SLOT_COUNT;
        if (player < 27)
        {
            return PLAYER_Y + player / 9 * SLOT;
        }
        return HOTBAR_Y;
    }

    public static int slotX(int index)
    {
        return cellX(index) + 1;
    }

    public static int slotY(int index)
    {
        return cellY(index) + 1;
    }
}
