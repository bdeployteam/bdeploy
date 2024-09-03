declare namespace Cypress {
  interface Chainable<Subject> {
    /**
     * @param repoName the name of the software repository.
     */
    createRepo(repoName: string);

    /**
     * @param repoName the name of the software repository.
     */
    enterRepo(repoName: string);

    /**
     * @param repoName the name of the software repository.
     * @param fileName the file within the fixtures directory to use.
     */
    uploadProductIntoRepo(repoName: string, fileName: string, screenshots?: boolean);
  }
}
