declare namespace Cypress {
  interface Chainable<Subject> {
    /**
     * @param buttonName the button's text to be pressed on the main page top bar.
     */
    pressMainNavTopButton(buttonName: string);

    /**
     * @param buttonName the button's text to be pressed on the main navigation bar.
     */
    pressMainNavButton(buttonName: string);

    /**
     * @param callback the callback to execute in the context of the main page content.
     */
    inMainNavContent(callback: (currentSubject: JQuery<HTMLElement>) => void);

    /**
     * @param componentName the tag name of the flyin component to expect
     * @param callback the callback to execute in that components context.
     */
    inMainNavFlyin(componentName: string, callback: (currentSubject: JQuery<HTMLElement>) => void);

    /**
     * Verify that the flyin is not visible on the page.
     */
    checkMainNavFlyinClosed();

    /**
     * @param buttonName the button's text to be pressed on the current context's dialog toolbar.
     */
    pressToolbarButton(buttonName: string);

    /**
     * @param name the name of the input field. nearest if undefined
     * @param data the text to fill into the input field.
     */
    fillFormInput(name: string, data: string);

    /**
     * @param name the name of the select field. nearest if undefined
     * @param data the option to select from the available values.
     */
    fillFormSelect(name: string, data: string);

    /**
     * @param name the name of the toggle to click. nearest if undefined
     */
    fillFormToggle(name: string);

    /**
     * @param filePath path of the file in the fixtures directory
     * @param mimeType the mime type of the file
     * @param checkEmpty whether to check if the initial state shows the 'no-image' image.
     */
    fillImageUpload(filePath: string, mimeType: string, checkEmpty?: boolean);

    /**
     * @param filePath path of the file in the fixtures directory
     * @param mimeType the mime type of the file
     */
    fillFileDrop(filePath: string, mimeType: string);

    /**
     * @param filePath check whether the file was uploaded successfully
     */
    checkFileUpload(filePath: string);

    /**
     * Creates a request to download a file from a link.
     *
     * @param link the object URL to download.
     */
    downloadObjectUrl(link: string): Promise<any>;

    /**
     * Chain off a clickable element which will trigger downloadLocation.click in the application.
     * @param filename the filename to store the file as - in the cypress/fixtures directory
     */
    downloadBlobFile(filename: string);

    /**
     * @param message the message to expect in the snackbar.
     */
    checkAndConfirmSnackbar(message: string);

    /**
     * Waits until the content of the page is loaded.
     * <p>
     * NOTE: Don't use with 'within', as elements would not be found in narrowed scope.
     */
    waitUntilContentLoaded();
  }
}
