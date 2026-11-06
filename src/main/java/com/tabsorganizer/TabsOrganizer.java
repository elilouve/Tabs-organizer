package com.tabsorganizer;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(TabsOrganizer.MOD_ID)
public class TabsOrganizer {
    public static final String MOD_ID = "tabsorganizer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TabsOrganizer() {
        LOGGER.info("Tabs Organizer loaded!");
    }
}
