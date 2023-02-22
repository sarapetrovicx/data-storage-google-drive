package paket;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import login.Login;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class GoogleDriveImplementation extends Specification{

    static Drive service;

    static {
        DriveManager.registerDrive(new GoogleDriveImplementation());
        try {
            service = Login.getDriveService();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    MyFile initMyFile(String file, String type) throws IOException, ParseException {
        MyFile myFile = new MyFile(type);

        File f = service.files().get(file).setFields("name,createdTime,modifiedTime").execute();
        myFile.setName(f.getName());
        myFile.setSize(findSize(file));
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");

        String[] splitCreated = f.getCreatedTime().toString().split("T");
        String[] splitModified = f.getModifiedTime().toString().split("T");
        myFile.setLastModified(String.valueOf(new SimpleDateFormat("yyyy-MM-dd").parse(splitModified[0])));
        myFile.setTimeCreated(String.valueOf(new SimpleDateFormat("yyyy-MM-dd").parse(splitCreated[0])));
        if(f.getCreatedTime() == null)
            myFile.setTimeCreated("0");
        return myFile;
    }

    private String findParentId(String file) throws IOException{
        File child = service.files().get(file)
                .setFields("parents")
                .execute();
        for(String id : child.getParents()) {
            File parent = service.files().get(id)
                    .execute();
            return parent.getId();
        }
        return null;
    }

    private long findSize(String dirPath) throws IOException {
        FileList result = null;
        File file = null;
        file = service.files().get(dirPath).execute();
//        System.out.println(file.getName());
        if(file.getMimeType().equals("application/vnd.google-apps.folder")){
            result = service.files().list()
                    .setQ("'" + dirPath + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id,name,createdTime,modifiedTime,size)")
                    .execute();
            int sum = 0;
            for(File f : result.getFiles()){
                if(f.getSize() != null)
                    sum += f.getSize();
            }
            return sum;
        } else {
            File fileMeta = service.files().get(dirPath).setFields("size").execute();
            if (fileMeta.getSize() == null)
                return 0;
            return fileMeta.getSize();
        }
    }

    private int findNumOfFiles(String dirPath) throws IOException {
        FileList result = null;
        File file = null;
        file = service.files().get(dirPath).execute();
//        System.out.println(file.getName());
        if(file.getMimeType().equals("application/vnd.google-apps.folder")){
            result = service.files().list()
                    .setQ("'" + dirPath + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, parents)")
                    .execute();
            int sum = 0;
            for(File f : result.getFiles()){
                sum += findNumOfFiles(f.getId());
            }
            return sum;
        } else {
            return 1;
        }
    }

    private boolean exists(String nameOfFile) throws IOException {
        FileList result = service.files().list()
                .setPageSize(10)
                .setFields("nextPageToken, files(id, name)")
                .execute();
        for(File f : result.getFiles()){
            if(f.getName().equalsIgnoreCase(nameOfFile))
                return true;
        }
        return false;
    }

    @Override
    boolean init(String path){
        File file = null;
        FileList result = null;
        try {
            file = service.files().get(path).execute();
        } catch (IOException e){
            System.out.println("Could not find file");
            return false;
        }
        try {
            result = service.files().list()
                    .setQ("'" + path + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, parents)")
                    .execute();
        } catch (IOException e){
            System.out.println("Is empty");
            defaultConf();
            return true;
        }
        if(file.getMimeType().equals("application/vnd.google-apps.folder")){
            boolean isRoot = false;
            if(result.getFiles().size() != 0) {
                System.out.println(result.getFiles().size());
                for (File f : result.getFiles()) {
                    if (f.getName().equals("configuration.txt")) {
                        isRoot = true;
                        break;
                    }
                }
            }
            return isRoot;
        }
        return false;
    }


    @Override
    boolean checkConfiguration(String parentFilePath, String ext) {
        File file = null;
        double bytes = 0;
        long size = 0;

        try{
            size = findNumOfFiles(parentFilePath);
            bytes = findSize(parentFilePath);
        } catch (IOException e){
            e.printStackTrace();
        }
//        String extension = file.getFileExtension();
        return !configuration.getExtensions().contains(ext) &&
                configuration.getNumOfFiles() > size && configuration.getSize() > bytes;
    }

    void writeInFile(Configuration configuration){
        ArrayList<String> ext = configuration.getExtensions();
        StringBuilder ex = new StringBuilder();
        for(String e: ext){
            ex.append(" ");
            ex.append(e);
        }
        FileWriter myWriter;
        try {
            System.out.println("uslo" + configuration.getExtensions().toString());
            myWriter = new FileWriter("./configuration.txt");
            myWriter.write("size: " + configuration.getSize() + "\n" +
                    "numOfFiles: " + configuration.getNumOfFiles() + "\n" +
                    "extension:" + ex);

            myWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    void writeConfiguraton(Configuration configuration) {
        File config = new File();
        config.setName("configuration.txt")
                .setParents(Collections.singletonList(getRootPath()));

        writeInFile(configuration);
        java.io.File filePath = new java.io.File("./configuration.txt");
        FileContent mediaContent = new FileContent("text/txt", filePath);

        try {
            service.files().create(config, mediaContent).execute();
        } catch (IOException e){
            e.printStackTrace();
        }

    }

    @Override
    void createRootDirectory() {
        File fileMetadata = new File();
        fileMetadata.setName("Storage");
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        File file = null;
        try {
            file = service.files().create(fileMetadata)
                    .setFields("id")
                    .execute();
            setRootPath(file.getId());
            defaultConf();
        }catch (IOException e){
            System.out.println("Could not create Storage");
        }

    }

    @Override
    void createRootDirectory(String path) {
        createRootDirectory(path, "Storage");
    }

    @Override
    void createRootDirectory(String path, String name) {
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setParents(Collections.singletonList(path));
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        File file = null;
        try {
            file = service.files().create(fileMetadata)
                    .setFields("id")
                    .execute();
            System.out.println(file.getId());
            setRootPath(file.getId());
            defaultConf();
        }catch (IOException e){
            System.out.println("Could not create Storage");
        }
    }

    @Override
    void createRootDirectory(String path, Configuration configuration) {
        createRootDirectory(path, "Storage", configuration);
    }

    @Override
    void createRootDirectory(String path, String name, Configuration configuration) {
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setParents(Collections.singletonList(path));
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        File file = null;
        try {
            file = service.files().create(fileMetadata)
                    .setFields("id")
                    .execute();
            System.out.println(file.getId());
            setRootPath(file.getId());
            makeConfiguration(configuration.getSize(), configuration.getNumOfFiles(), configuration.getExtensions());
        }catch (IOException e){
            System.out.println("Could not create Storage");
        }
    }

    @Override
    void createRootDirectory(Configuration configuration) {
        File fileMetadata = new File();
        fileMetadata.setName("Storage");
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        File file = null;
        try {
            file = service.files().create(fileMetadata)
                    .setFields("id")
                    .execute();
            setRootPath(file.getId());
            makeConfiguration(configuration.getSize(), configuration.getNumOfFiles(), configuration.getExtensions());
        }catch (IOException e){
            System.out.println("Could not create Storage");
        }
        //System.out.println(file.getId());

    }

    int cnt = 1;
    @Override
    void makeDirectory(){
        String root = getRootPath();

        File fileMetadata = new File();
        fileMetadata.setName("Directory");
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(Collections.singletonList(root));

        if(checkConfiguration(root, "dir")){
            try {
                if (exists("Directory")) {
                    String name = "Directory" + cnt;
                    while (exists(name)){
                        name = "Directory" + cnt++;
                    }
                    fileMetadata.setName(name);
                }
                service.files().create(fileMetadata)
                        .setFields("id")
                        .execute();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    @Override
    void makeDirectory(String name) {
        String root = getRootPath();

        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(Collections.singletonList(root));

        try {
            if(checkConfiguration(root, fileMetadata.getId()) && !exists(name)) {
                service.files().create(fileMetadata)
                        .setFields("id")
                        .execute();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    void makeDirectories(int numOfDir) {
        for(int i = 1; i <= numOfDir; i++){
            makeDirectory("dir" + i);
        }
    }

    @Override
    void makeDirectories(int numOfDir, ArrayList<String> names) {
        if(numOfDir <= names.size()){
            for(int i = 0; i < numOfDir; i++){
                makeDirectory(names.get(i));
            }
        } else {
            System.out.println("Wanted number of directories is bigger than the size of the list");
        }
    }

    @Override
    void copyFile(String source, String target) throws IOException {
        File sourceFile = service.files().get(source).execute();
        int cnt = 1;
        String newName = sourceFile.getName() + "_copy";

        if(checkConfiguration(target, source)) {
            if (exists(newName)){
                newName = newName + cnt;
                while (exists(newName)){
                    newName = newName + cnt++;
                }
            }
            File copiedFile = new File();
            copiedFile.setName(newName).setParents(Collections.singletonList(target));
            service.files().copy(source, copiedFile).execute();
        }
    }

    @Override
    void copyFiles(ArrayList<String> sources, String target) throws IOException {
        for(String path: sources){
            copyFile(path, target);
        }
    }

    @Override
    void deleteFile(String path) throws IOException {
        service.files().delete(path).execute();
    }

    @Override
    void deleteFiles(ArrayList<String> paths) throws IOException {
        for(String path: paths)
            deleteFile(path);
    }

    @Override
    void deleteDirectory(String path) throws IOException {
        deleteFile(path);
    }

    @Override
    void deleteDirectories(ArrayList<String> paths) throws IOException {
        deleteFiles(paths);
    }

    @Override
    void moveFile(String source, String to) throws IOException {
        File file = service.files().get(source)
                .setFields("parents")
                .execute();
        StringBuilder previousParents = new StringBuilder();
        for (String parent : file.getParents()) {
            previousParents.append(parent);
            previousParents.append(',');
        }
            // Move the file to the new folder
            service.files().update(source, null)
                    .setAddParents(to)
                    .setRemoveParents(previousParents.toString())
                    .setFields("id, parents")
                    .execute();

    }

    @Override
    void moveFiles(List<String> from, String to) throws IOException {
        for(String path: from)
            moveFile(path, to);
    }

    @Override
    void downloadFile(String from, String to) throws IOException {
            File file = service.files().get(from).execute();
            FileOutputStream fis = new FileOutputStream(to + java.io.File.separator + file.getName());
            service.files().get(from)
                    .executeMediaAndDownloadTo(fis);
    }

    @Override
    void downloadFiles(List<String> from, String to) throws IOException {
        for(String s: from)
            downloadFile(s, to);
    }

    @Override
    void renameFile(String path, String newName) {
        try {
            File copiedFile = new File();
            copiedFile.setName(newName);
            service.files().copy(path, copiedFile).execute();
            service.files().delete(path).execute();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    @Override
    void renameDirectory(String path, String newName) {
        renameFile(path, newName);
        //TODO: testiraj
    }

    @Override
    List<MyFile> returnFilesInDirectory(String path) throws IOException, ParseException {
        List<MyFile> list = new ArrayList<>();
        File file = service.files().get(path).execute();
        if(file.getMimeType().equals("application/vnd.google-apps.folder")){
            System.out.println("Folder: " + file.getName() + " (" + path + ")");
            FileList result = service.files().list()
                    .setQ("'" + path + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("files(id,name,createdTime,modifiedTime,size)")
                    .execute();
            List<File> files = result.getFiles();
            if (files == null || files.isEmpty()) {
                System.out.println("No files found.");
            } else {
                for (File f : files) {
                    if(file.getMimeType().equals("application/vnd.google-apps.folder"))
                        list.add(initMyFile(f.getId(), "directory"));
                    else
                        list.add(initMyFile(f.getId(), "file"));
                }
            }
        } else {
//            System.out.println(file.getName() + " (" + dirID + ")" + " " + file.getSize());
        }
        return list;
    }

    @Override
    List<MyFile> filesFromDirectories(String path) {
        return null;
    }

    @Override
    List<MyFile> filesFromDirectoriesAndSubdirectories(String path) throws IOException, ParseException {
        java.io.File[] listOfFiles;
        List<MyFile> list = new ArrayList<>();
        File file = service.files().get(path).execute();
        if(file.getMimeType().equals("application/vnd.google-apps.folder")){
            System.out.println("Folder: " + file.getName() + " (" + path + ")");
            FileList result = service.files().list()
                    .setQ("'" + path + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("files(id,name,createdTime,modifiedTime,size)")
                    .execute();
            List<File> files = result.getFiles();
            if (files == null || files.isEmpty()) {
                System.out.println("No files found.");
            } else {
                for (File f : files) {
                    if(f.getMimeType().equals("application/vnd.google-apps.folder"))
                        list.add(initMyFile(f.getId(),"directory"));
                    else
                        list.add(initMyFile(f.getId(),"file"));
                }
            }
        } else {
            System.out.println("Select folder, not file");
        }
        return list;
    }

    @Override
    List<MyFile> returnFilesWithExt(String path, String extension) throws IOException, ParseException {
        java.io.File[] listOfFiles;
        List<MyFile> list = new ArrayList<>();
        File file = service.files().get(path).execute();
        if(file.getMimeType().equals("application/vnd.google-apps.folder")) {
            FileList result = service.files().list()
                    .setQ("'" + path + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("files(id,name,createdTime,modifiedTime,size,fileExtension)")
                    .execute();
            List<File> files = result.getFiles();
            if (files == null || files.isEmpty()) {
                System.out.println("No files found.");
            } else {
                for (File f : files) {
                    if (f.getFileExtension() != null && f.getFileExtension().equals(extension)) {
                        list.add(initMyFile(f.getId(), "file"));
                    }
                }
            }
        }
        return list;
    }

    @Override
    List<MyFile> returnFilesWithSubstring(String path, String substring) throws IOException, ParseException {
        java.io.File[] listOfFiles;
        List<MyFile> list = new ArrayList<>();
        File file = service.files().get(path).execute();
        if(file.getMimeType().equals("application/vnd.google-apps.folder")) {
            System.out.println("Folder: " + file.getName() + " (" + path + ")");
            FileList result = service.files().list()
                    .setQ("'" + path + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("files(id,name,createdTime,modifiedTime,size,mimeType)")
                    .execute();
            List<File> files = result.getFiles();
            if (files == null || files.isEmpty()) {
                System.out.println("No files found.");
            } else {
                for (File f : files) {
                    if (f.getName().contains(substring)) {
                        if(f.getMimeType().equals("application/vnd.google-apps.folder"))
                            list.add(initMyFile(f.getId(),"directory"));
                        else
                            list.add(initMyFile(f.getId(),"file"));
                    }
                }
            }
        }
        return list;
    }

    @Override
    boolean containsFile(String path, String fileName) throws IOException {
        File file = service.files().get(path).execute();
        if(file.getMimeType().equals("application/vnd.google-apps.folder")) {
            System.out.println("Folder: " + file.getName() + " (" + path + ")");
            FileList result = service.files().list()
                    .setQ("'" + path + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("files(id,name,createdTime,modifiedTime,size)")
                    .execute();
            List<File> files = result.getFiles();
            if (files == null || files.isEmpty()) {
                System.out.println("No files found.");
            } else {
                for (File f : files) {
                    if (f.getName().equals(fileName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    boolean containsFiles(String path, ArrayList<String> fileNames) throws IOException {
        for(String name: fileNames){
            if(!containsFile(path, name))
                return false;
        }
        return true;
    }

    @Override
    String findFile(String fileName) throws IOException {
        if(exists(fileName))
            return findParentId(fileName);
        return null;
    }

    @Override
    List<MyFile> sortFiles(String path, boolean asc, SortBy by) throws IOException, ParseException {
        //TODO: testiraj
        File file = service.files().get(path).execute();
        FileList result = null;
        if(file.getMimeType().equals("application/vnd.google-apps.folder")) {
            if(by.equals(SortBy.DATE_MODIFIED)) {
                if (asc) {
                    result = service.files().list()
                            .setQ("'" + path + "' in parents and trashed = false")
                            .setSpaces("drive")
                            .setFields("files(id,name,createdTime,modifiedTime,size,mimeType)")
                            .setOrderBy("modifiedTime")
                            .execute();
                } else {
                    result = service.files().list()
                            .setQ("'" + path + "' in parents and trashed = false")
                            .setSpaces("drive")
                            .setFields("files(id,name,createdTime,modifiedTime,size,mimeType)")
                            .setOrderBy("modifiedTime desc")
                            .execute();
                }
            } else if(by.equals(SortBy.NAME)) {
                if (asc) {
                    result = service.files().list()
                            .setQ("'" + path + "' in parents and trashed = false")
                            .setSpaces("drive")
                            .setFields("files(id,name,createdTime,modifiedTime,size,mimeType)")
                            .setOrderBy("name")
                            .execute();
                } else {
                    result = service.files().list()
                            .setQ("'" + path + "' in parents and trashed = false")
                            .setSpaces("drive")
                            .setFields("files(id,name,createdTime,modifiedTime,size,mimeType)")
                            .setOrderBy("name desc")
                            .execute();
                }
            } else if(by.equals(SortBy.DATE_CREATED)) {
                if (asc) {
                    result = service.files().list()
                            .setQ("'" + path + "' in parents and trashed = false")
                            .setSpaces("drive")
                            .setFields("files(id,name,createdTime,modifiedTime,size,mimeType)")
                            .setOrderBy("createdTime")
                            .execute();
                } else {
                    result = service.files().list()
                            .setQ("'" + path + "' in parents and trashed = false")
                            .setSpaces("drive")
                            .setFields("files(id,name,createdTime,modifiedTime,size,mimeType)")
                            .setOrderBy("createdTime desc")
                            .execute();
                }
            } else if(by.equals(SortBy.SIZE)) {
                if (asc) {
                    result = service.files().list()
                            .setQ("'" + path + "' in parents and trashed = false")
                            .setSpaces("drive")
                            .setFields("files(id,name,createdTime,modifiedTime,size,mimeType)")
                            .setOrderBy("quotaBytesUsed")
                            .execute();
                } else {
                    result = service.files().list()
                            .setQ("'" + path + "' in parents and trashed = false")
                            .setSpaces("drive")
                            .setFields("files(id,name,createdTime,modifiedTime,size,mimeType)")
                            .setOrderBy("quotaBytesUsed desc")
                            .execute();
                }
            }
            List<MyFile> files = new ArrayList<>();
            for(File f : result.getFiles()){
                if(f.getMimeType().equals("application/vnd.google-apps.folder"))
                    files.add(initMyFile(f.getId(), "director"));
                else
                    files.add(initMyFile(f.getId(), "file"));
            }
            return files;
        } else
            System.out.println("Path has to be directory");
        return null;
    }

    @Override
    List<MyFile> createdOrModifiedWithinTimePeriod(String path, String from, String to, String type) throws IOException, ParseException {
        ArrayList<MyFile> myFiles = new ArrayList<>();
        MyFile myFile = null;
        Date fromDate = null;
        Date toDate = null;
        fromDate = new SimpleDateFormat("dd/MM/yyyy").parse(from);
        toDate = new SimpleDateFormat("dd/MM/yyyy").parse(to);
        File file = service.files().get(path).execute();
        if(file.getMimeType().equals("application/vnd.google-apps.folder")) {
            System.out.println("Folder: " + file.getName() + " (" + path + ")");
            FileList result = service.files().list()
                    .setQ("'" + path + "' in parents and trashed = false")
                    .setSpaces("drive")
                    .setFields("files(id,name,createdTime,modifiedTime,size,mimeType)")
                    .execute();
            List<File> files = result.getFiles();
            if (files == null || files.isEmpty()) {
                System.out.println("No files found.");
            } else {
                for (File f : files) {
                    if(f.getMimeType().equals("application/vnd.google-apps.folder"))
                        myFile = initMyFile(f.getId(), "directory");
                    else
                        myFile = initMyFile(f.getId(), "file");
                }
            }
        }
        Date date = null;
        if (type.equalsIgnoreCase("modified"))
            date = Objects.requireNonNull(myFile).stringToDate("modified");
        else if (type.equalsIgnoreCase("created"))
            date = Objects.requireNonNull(myFile).stringToDate("created");
        if (date != null && date.after(fromDate) && date.before(toDate))
            myFiles.add(myFile);
        return myFiles;
    }

    @Override
    List<String> filter(List<MyFile> results, SortBy filter) {
        List<String> list = new ArrayList<>();
        if(results.isEmpty())
            return null;
        switch (filter){
            case NAME:
                for(MyFile f:results)
                    list.add(f.getName());
                break;
            case SIZE:
                for(MyFile f:results)
                    list.add(String.valueOf(f.getSize()));
                break;
            case DATE_CREATED:
                for(MyFile f:results)
                    list.add(f.getTimeCreated());
                break;
            case DATE_MODIFIED:
                for(MyFile f:results)
                    list.add(f.getLastModified());
                break;
        }
        return list;
    }
}
