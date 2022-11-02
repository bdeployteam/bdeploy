---
order: 8
---
# Common UI Elements

**BDeploy** tries to follow a few quite simple patterns regarding UI/UX. The common parts of the UI which is the same for nearly all pages is described in this section.

## Search

The search bar at the top of the screen can be used to search in the current page. It will filter all tables and lists in the current page, regardless of where the table is located.

:::{align=center}
![Search Bar](/images/Doc_SearchBarEnabled.png){width=480}
:::

The search bar might be disabled in case there is no element on the screen currently which can be filtered.

:::{align=center}
![Disabled Search Bar](/images/Doc_SearchBarDisabled.png){width=480}
:::

## Panel

The panel is the area on the left side of the screen which typically contains content related to the content in the main area. This panel is invisible by default, and slides into view when the according content is available. This is typically triggered by clicking an entry in a table or a toolbar button. An example for a panel is the _Create Instance Group_ panel, which will also be one of the first panels you'll see in the application.

:::{align=center}
![Panel](/images/Doc_AddGroupPanelEmpty.png){width=480}
:::

## Table vs. Card View

A lot of tables throughout the **BDeploy** UI offer the possibility to display them as cards instead of as a list. This is done by clicking the corresponding button in the toolbar of each dialog.

:::{align=center}
![Table Mode](/images/Doc_ModeTable.png){width=480}
:::
:::{align=center}
![Card Mode](/images/Doc_ModeCards.png){width=480}
:::

## Grouping

Most tables offer the possibility to group the entries by a certain criteria. The criteria may be predefined or freely configurable, depending on the type of data in the table. Grouping can be changed using the _Data Grouping_ toolbar button.

:::{align=center}
![Data Grouping](/images/Doc_GroupingPanel.png){width=480}
:::

Groups are represented as collapsable sections in the table. Multiple levels of grouping are supported in table mode. Press the [ **+** ] button to add an additional level of grouping and choose a criteria to group. Pressing the [ **-** ] button will remove the last level of grouping.

The _Save as local preset_ button will save the current data grouping settings as preset in the browser storage. This will be the default grouping settings whenever you open the same page again.

!!!info Note
Grouping works differently in _Card Mode_. Only one level of grouping is supported in card mode, and groups are displayed as tabs instead of sections.
!!!

## Deep Links

This not actually a visible element of the **BDeploy** UI, but a feature you can use when sharing links with colleagues. All locations in **BDeploy** are deep-link capable, meaning you can copy the current URL from the browser and send it to somebody else. When opened, the other Browser will see exactly the same page as you do, including the currently open panel.
