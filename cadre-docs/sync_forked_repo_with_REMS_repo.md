# Sync the forked CADRE Backend application repository with the original REMS repository

### To sync your forked repository with the original repository, you can follow these steps:
<br>
1. First, make sure you have a local copy of the forked repository on your computer. If you haven't cloned it yet, you can do so with the following command:
    
    ```
    git clone https://github.com/ADA-ANU/rems.git
    ```

2. Once you have a local copy of the forked repository, navigate to the repository directory in your terminal by using the command cd your-forked-repo.

3. Add the original repository as a remote repository with the following command:
    ```
    git remote add upstream https://github.com/CSCfi/rems.git
    ```

4. Fetch the latest changes from the original repository using the following command:
    ```
    git fetch upstream
    ```
5. Switch to the branch you want to sync with the original repository. For example, if you want to sync with the master branch, use the following command:
    ```
    git checkout master
    ```
6. Merge the changes from the original repository into your local repository using the following command:
    ```
    git merge upstream/master
    ```
7. If there are any conflicts, resolve them and commit the changes.

8. Finally, push the changes to your forked repository with the following command:
    ```
    git push origin master
    ```
<br>

## The alternative the easy way to sync the CADRE Backed application repository (https://github.com/CSCfi/rems.git) with the original REMS repository is by using GitHub website.

### Follow below steps to sync using GitHub website:

1. Login to GitHub
2. Navigate to Organisation (ADA-ANU)
3. In Repositories tab, select **rems** repository
4. Click on **Sync fork** dropdown

![alt text](https://github.com/ADA-ANU/rems/blob/master/cadre-docs/images/sync_forked_repo_in_github.png?raw=true)

