/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.tea.plugin;

import org.eclipse.tea.core.TeaMenuTopLevelGrouping;
import org.eclipse.tea.core.services.TaskingMenuDecoration;
import org.osgi.service.component.annotations.Component;

@Component
public class BDeployMenuDecoration implements TaskingMenuDecoration {

    public static final String MENU_BDEPLOY = "BDeploy";

    @TaskingMenuPathDecoration(menuPath = MENU_BDEPLOY, groupingId = TeaMenuTopLevelGrouping.GRP_ADVANCED)
    public static final String DECO_BDEPLOY_MENU = "icons/bdeploy.png";

}
