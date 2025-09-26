import javax.swing.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class HardDiskDriver { // A custom hard disk driver for my CPU emulator. UNFINISHED. WORK IN PROGRESS

    private RandomAccessFile diskFile;

    public static final int INODE_ENTRY_END = 0xFD;
    public static final int DIRECT_INODE_POINTER_MAX_COUNT = 15;
    public static final int INDIRECT_INODE_POINTER_MAX_COUNT = 40;
    public static final int DOUBLE_INDIRECT_INODE_POINTER_MAX_COUNT = 9;
    public static final int MAX_FILE_NAME_LENGTH_B = 32;
    public static final int MAX_FILE_ENTRY_LENGTH_B = MAX_FILE_NAME_LENGTH_B + 4 + 1;
    public static final int MAX_INODE_SIZE_B = 2 + 1 + 1 + (17 * ( Integer.SIZE / 8 ));

    // initialization variables
    private int diskSize;
    public float diskSizeMB = 5;
    private float diskSizeKB;
    private int[] blockLocations;
    private int blockCount;

    String sourceDebug = "HARD_DISK_DRIVER";

    public final int blockSizeB = 1024; // the block size in bytes

    // the hard drive blocks starting/ending addresses
    private int superBlockStartAddress = 0, superBlockEndAddress = blockSizeB - 1;
    private int bitMapBlockStartAddress = superBlockEndAddress + 1, bitMapBlockEndAddress;

    private int InodeTableBlockStartAddress, InodeTableBlockEndAddress,
                InodeIndirectBlockStartAddress, InodeIndirectBlockEndAddress,
                InodeDoubleIndirectBlockStartAddress, InodeDoubleIndirectBlockEndAddress;

    private int fileNameBlockStartAddress, fileNameBlockEndAddress;
    private int contentStartAddress, contentEndAddress;

    // the length of each block (in blocks).
    private int superBlockLength = 1,
            bitMapBlockLength,
            InodeTableBlockLength = 32, InodeIndirectBlockLength = 32, InodeDoubleIndirectBlockLength = 32,
            fileNameBlockLength = 16;
    // the length of each block (in bytes)
    private int superBlockLengthB = blockSizeB,
            bitMapBlockLengthB,
            InodeTableBlockLengthB, InodeIndirectBlockLengthB, InodeDoubleIndirectBlockLengthB,
            fileNameBlockLengthB;

    private BitSet blockBitMap;


    private boolean checkHardDriveFile(String filePath){
        return Files.exists(Path.of(filePath));
    }

    public void closeDrive() {
        try {
            diskFile.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void triggerHardDriveError(String err){
        if (VirtualMachine.ui) JOptionPane.showMessageDialog(null, err, "Hard Drive error", JOptionPane.ERROR_MESSAGE);
        else System.out.println(err);
    }

    public void calculateDiskSegments() throws IOException{
        // made it into an int because
        // 1- the file length is small it can fit into an int
        // 2- prevent the headache of type incompatibility.
        diskSize = Math.toIntExact(diskFile.length());
        diskSizeKB = diskSize / 1024.0f;

        System.out.println("Calculating the hard drive blocks.");
        Logger.addLog("Calculating the hard drive blocks.");

        blockCount = diskSize / blockSizeB;

        blockLocations = new int[blockCount];
        int currentBlockPointer = 0x0;
        for(int i = 0; i < blockLocations.length; i++){
            blockLocations[i] = currentBlockPointer;
            currentBlockPointer += blockSizeB;
        }

        bitMapBlockLengthB = (int) Math.ceil((double) blockCount / 8);
        bitMapBlockLength = (int) Math.ceil((double) bitMapBlockLengthB / blockSizeB);
        bitMapBlockLengthB = bitMapBlockLength * blockSizeB;
        bitMapBlockEndAddress = bitMapBlockStartAddress + bitMapBlockLengthB;

        InodeTableBlockLengthB = InodeTableBlockLength * blockSizeB;
        InodeTableBlockStartAddress = bitMapBlockEndAddress;
        InodeTableBlockEndAddress = InodeTableBlockStartAddress + InodeTableBlockLengthB;

        InodeIndirectBlockLengthB = InodeIndirectBlockLength * blockSizeB;
        InodeIndirectBlockStartAddress = InodeTableBlockEndAddress;
        InodeIndirectBlockEndAddress = InodeIndirectBlockStartAddress + InodeIndirectBlockLengthB;

        InodeDoubleIndirectBlockLengthB = InodeDoubleIndirectBlockLength * blockSizeB;
        InodeDoubleIndirectBlockStartAddress = InodeIndirectBlockEndAddress;
        InodeDoubleIndirectBlockEndAddress = InodeDoubleIndirectBlockStartAddress + InodeDoubleIndirectBlockLengthB;

        fileNameBlockLengthB = fileNameBlockLength * blockSizeB;
        fileNameBlockStartAddress = InodeDoubleIndirectBlockEndAddress;
        fileNameBlockEndAddress = fileNameBlockStartAddress + fileNameBlockLengthB;

        contentStartAddress = fileNameBlockEndAddress;

        String init = String.format("""
                total number of blocks : %d
                Super block address : 0x%06X, number of blocks reserved %d, total length in bytes %d
                BitMap block address : 0x%06X, number of blocks reserved %d, total length in bytes %d
                Inode block address : 0x%06X, number of blocks reserved %d, total length in bytes %d
                Inode Indirect block address : 0x%06X, number of blocks reserved %d, total length in bytes %d
                Inode Double Indirect block address : 0x%06X, number of blocks reserved %d, total length in bytes %d
                file names block address : 0x%06X, number of blocks reserved %d, total length in bytes %d
                content blocks address : 0x%06X
                """, blockCount ,
                superBlockStartAddress, superBlockLength, superBlockLengthB,
                bitMapBlockStartAddress, bitMapBlockLength, bitMapBlockLengthB,
                InodeTableBlockStartAddress, InodeTableBlockLength,InodeTableBlockLengthB,
                InodeIndirectBlockStartAddress, InodeIndirectBlockLength, InodeIndirectBlockLengthB,
                InodeDoubleIndirectBlockStartAddress, InodeDoubleIndirectBlockLength, InodeDoubleIndirectBlockLengthB,
                fileNameBlockStartAddress, fileNameBlockLength, fileNameBlockLengthB,
                contentStartAddress);

        System.out.println(init);
        Logger.addLog(init);
    }

    public HardDiskDriver(String diskImagePath){

        Logger.source = sourceDebug;
        boolean isFirstCreation = false;
        System.out.println("Initializing hard drive.");
        Logger.addLog("Initializing hard drive.");

        try {

            if (!checkHardDriveFile(diskImagePath)){
                isFirstCreation = true;

                System.out.println("No hard drive file found... creating new one.");
                Logger.addLog("No hard drive file found... creating new one.");

                diskFile = new RandomAccessFile(diskImagePath, "rw");
                diskFile.setLength((long) (diskSizeMB * 1024 * 1024));
                diskFile.seek(superBlockStartAddress);

                diskFile.writeFloat(diskSizeMB); // disk size
                diskFile.writeInt(blockSizeB); // the block size
                diskFile.writeBytes("T.K.Y 13/9/2025"); // my signature
            }else diskFile = new RandomAccessFile(diskImagePath, "rw");

            calculateDiskSegments();
            if (isFirstCreation) {
                diskFile.seek(InodeTableBlockStartAddress); diskFile.writeByte(INODE_ENTRY_END);
                diskFile.seek(InodeIndirectBlockStartAddress); diskFile.writeByte(INODE_ENTRY_END);
                diskFile.seek(InodeDoubleIndirectBlockStartAddress); diskFile.writeByte(INODE_ENTRY_END);
                diskFile.seek(fileNameBlockStartAddress); diskFile.writeByte(INODE_ENTRY_END);
            }

            byte[] all = new byte[bitMapBlockLengthB];
            diskFile.seek(bitMapBlockStartAddress);
            diskFile.read(all, 0, bitMapBlockLengthB);
            blockBitMap = BitSet.valueOf(all);
            blockBitMap.set((bitMapBlockLengthB - 1) * 8);
            blockBitMap.clear((bitMapBlockLengthB - 1) * 8);

            if (isFirstCreation) {
                int bitMapEndBlock = addressToBlock(bitMapBlockEndAddress);
                for (int i = 0; i < bitMapEndBlock; i++) blockBitMap.set(i);
                setBlockUsed(0);
                setBlockUsed(1);
            }

            String spaceInfo = String.format("Free space : %sB (%s MB)\nSpace occupied : %sB (%s MB)\n",
                    getFreeSpaceBytes(), getFreeSpaceBytes() / 1e+6,
                     diskSize - getFreeSpaceBytes(), (diskSize - getFreeSpaceBytes()) / 1e+6);

            System.out.println(spaceInfo);
            Logger.addLog(spaceInfo);

            System.out.println("Hard drive ready...");
            Logger.addLog("Hard drive ready...");

        }catch (IOException e){
            e.printStackTrace();
            Launcher.triggerLaunchError("Failed to initialize hard drive.");
        }
    }

    // BLOCK BITMAP OPERATIONS
    public void setBlockUsed(int blockIndex) throws IOException {
        long pos = diskFile.getFilePointer();
        blockBitMap.set(blockIndex);
        diskFile.seek(bitMapBlockStartAddress);
        diskFile.write(blockBitMap.toByteArray());
        diskFile.seek(pos);
    }
    public void setBlockAvailable(int blockIndex) throws IOException{
        long pos = diskFile.getFilePointer();
        blockBitMap.clear(blockIndex);
        diskFile.seek(bitMapBlockStartAddress);
        diskFile.write(blockBitMap.toByteArray());
        diskFile.seek(pos);
    }
    public void toggleBlock(int blockIndex) throws IOException{
        long pos = diskFile.getFilePointer();
        blockBitMap.flip(blockIndex);
        diskFile.seek(bitMapBlockStartAddress);
        diskFile.write(blockBitMap.toByteArray());
        diskFile.seek(pos);
    }
    public void setBlock(int blockIndex, boolean value) throws IOException{
        long pos = diskFile.getFilePointer();
        blockBitMap.set(blockIndex, value);
        diskFile.seek(bitMapBlockStartAddress);
        diskFile.write(blockBitMap.toByteArray());
        diskFile.seek(pos);
    }

    public int getBlockStatus(int blockIndex) throws IOException{
        return blockBitMap.get(blockIndex) ? 1 : 0;
    }
    public boolean isBlockUsed(int blockIndex){
        return blockBitMap.get(blockIndex);
    }

    // INODE TABLE FUNCTIONS //


    public void createInodeTableEntry(String fileName, int size, int block_count, int[] block_pointers) throws IOException {
        int currentPos = Math.toIntExact(diskFile.getFilePointer());
        int writePos = getFirstAvailableInodePosition();
        int bytePadding = 0;

        //System.out.printf("Found space for inode entry at 0x%06X\n", writePos);
        Logger.addLog(String.format("Found space for inode entry at 0x%06X", writePos));

        diskFile.seek(writePos);

        // inode table entry structure
        // 1- file size (2 bytes)
        // 2- block count (1 byte)
        // 3- block pointers (4 bytes each) (17 pointers max with a total of 68 bytes)
        // 4- inode entry end marker (1 byte)
        // total : 72 bytes

        // file names are stored at their own blocks.

        diskFile.writeShort(size);
        diskFile.writeByte(block_count);
        bytePadding += 3;

        for(int i = 0; i < block_count; i++){
            diskFile.writeInt(blockLocations[ block_pointers[i] ]);
            setBlockUsed(block_pointers[i]);
            bytePadding += 4;
        }
        diskFile.skipBytes(MAX_INODE_SIZE_B - bytePadding);
        diskFile.writeByte(INODE_ENTRY_END);

        // file name entry structure
        //
        // 1- file name (32 bytes) (end marked with a null terminator)
        // 2- pointer to the inode entry of the file (4 bytes)
        // 3- inode end marker (1 byte)
        // total : 37 bytes

        int namePos = getFirstAvailableFileNameEntry();
        //System.out.printf("Found space for file name entry at 0x%06X\n", namePos);
        Logger.addLog( String.format("Found space for file name entry at 0x%06X", namePos) );

        diskFile.seek(namePos);

        byte[] nameBytes = new byte[MAX_FILE_NAME_LENGTH_B];
        System.arraycopy(fileName.getBytes(), 0, nameBytes, 0, fileName.getBytes().length);

        diskFile.writeBytes(new String(nameBytes) + "\0");
        diskFile.writeInt(writePos);
        diskFile.writeByte(INODE_ENTRY_END);

        diskFile.seek(currentPos);
    }

    public void overwriteInodeEntry(int inodeAddress, int size, int block_count, int[] block_pointers){
        try {
            int currentPos = Math.toIntExact(diskFile.getFilePointer());
            diskFile.seek(inodeAddress);

            for(int i = 0; i < MAX_INODE_SIZE_B; i++) diskFile.writeByte(0x00);
            diskFile.seek(inodeAddress);

            int bytePadding = 0;
            diskFile.writeShort(size);
            diskFile.writeByte(block_count);
            bytePadding += 3;

            for(int i = 0; i < block_pointers.length; i++) {
                diskFile.writeInt( blockLocations[ block_pointers[i] ] );
                if (!isBlockUsed( block_pointers[i] )) setBlockUsed( block_pointers[i] );
                bytePadding += 4;
            }

            diskFile.skipBytes( MAX_INODE_SIZE_B - bytePadding );
            diskFile.writeByte(INODE_ENTRY_END);

            diskFile.seek(currentPos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getFileInodeAddress(String fileName) throws IOException {
        int currentPos = Math.toIntExact(diskFile.getFilePointer());

        int pos = getNameEntryAddress(fileName, true);
        if (pos == -1) return -1;
        else{
            diskFile.seek(pos);
            int targetPos = diskFile.readInt();
            diskFile.seek(currentPos);
            return targetPos;
        }
    }

    public int getNameEntryAddress(String fileName, boolean returnNameEndPosition) throws IOException {
        int currentPos = Math.toIntExact(diskFile.getFilePointer());
        diskFile.seek(fileNameBlockStartAddress + 1); // skip the first node end marker

        StringBuilder name = new StringBuilder();
        while (diskFile.getFilePointer() <= fileNameBlockEndAddress) {

            int c = diskFile.readByte() & 0xff;
            if (c != 0x0) name.append((char) c);

            else {
                if (fileName.contentEquals(name)) {
                    int targetPos;
                    // sub 1 to compensate for the null terminator byte
                    if(!returnNameEndPosition) targetPos = Math.toIntExact(diskFile.getFilePointer() - name.length() - 1);
                    else targetPos = Math.toIntExact( diskFile.getFilePointer() + (MAX_FILE_NAME_LENGTH_B - name.length()) );
                    diskFile.seek(currentPos);
                    return targetPos;
                }
                // null terminator byte + inode entry pointer length = 5 bytes
                diskFile.skipBytes(MAX_FILE_NAME_LENGTH_B - name.length() + 5);
                name = new StringBuilder();
            }
        }
        diskFile.seek(currentPos);
        return -1;
    }

    public int getFirstAvailableFileNameEntry() throws IOException {
        diskFile.seek(fileNameBlockStartAddress + 1);
        while (diskFile.getFilePointer() <= fileNameBlockEndAddress){

            if (diskFile.readShort() == 0) return Math.toIntExact(diskFile.getFilePointer() - 2);
            else{
                diskFile.skipBytes(MAX_FILE_ENTRY_LENGTH_B - 1);
            }
        }
        return -1;
    }
    public int getLastAvailableFileNameEntry() throws IOException{
        diskFile.seek(fileNameBlockEndAddress);
        int x = fileNameBlockEndAddress;
        while (x >= fileNameBlockStartAddress){
            if ((diskFile.readByte() & 0xff) == INODE_ENTRY_END) return x;
            x--;
            diskFile.seek(x);
        }
        return -1;
    }

    public int getLastInodeTablePosition() throws IOException {
        diskFile.seek(InodeTableBlockEndAddress);
        int x = InodeTableBlockEndAddress;
        while (x <= InodeTableBlockEndAddress && x >= InodeTableBlockStartAddress){

            diskFile.seek(x);
            // the last table position is the position of the very first inode end marker from the end of the inode table blocks
            if ( (diskFile.readByte() & 0xff) == INODE_ENTRY_END ) return x;
            x--;

        }
        return -1;
    }

    public int getFirstAvailableInodePosition() throws IOException{
        diskFile.seek(InodeTableBlockStartAddress + 1);

        while (diskFile.getFilePointer() <= InodeTableBlockEndAddress){

            if (diskFile.readShort() == 0) return Math.toIntExact( diskFile.getFilePointer() - 2 );
            else {
                //System.out.printf("Inode entry at 0x%06X is busy. skipping.\n", diskFile.getFilePointer() - 2);
                diskFile.skipBytes(MAX_INODE_SIZE_B - 1);
            }
        }
        return -1;
    }

    public int getFirstAvailableBlockIndex(){
        for(int i = 0; i < blockCount; i++) {
            if (!isBlockUsed(i)) return i;
        }
        return -1;
    }
    public int getFirstAvailableContentBlock(){
        for(int i = addressToBlock(contentStartAddress); i < blockCount; i++) {
            if (!isBlockUsed(i)) return i;
        }
        return -1;
    }

    public int[] allocateBlocksToFile(int lengthB) throws IOException {
        int[] blocksAllocated = new int[(int) Math.ceil( (double) lengthB / blockSizeB )];
        if (getFreeSpaceBytes() < lengthB) {
            // TODO : add error handling
        }else{
            for(int i = 0; i < blocksAllocated.length; i++){
                int index = getFirstAvailableContentBlock();
                blocksAllocated[i] = index;
                setBlockUsed(index);
                //System.out.printf("Block #%d -> (located at address : 0x%06X) allocated.\n", index, blockToAddress(index));
                Logger.addLog( String.format("Block #%d -> (located at address : 0x%06X) allocated.", index, blockToAddress(index)) );
            }
        }

        return blocksAllocated;
    }

    public int[] allocateBlocksToFile(int lengthB, int[] prev_blocks) throws IOException {
        int[] blocksAllocated = new int[ (int) Math.ceil( (double) lengthB / blockSizeB ) + prev_blocks.length ];
        int index = 0;
        for(; index < prev_blocks.length; index++) blocksAllocated[index] = prev_blocks[index];

        // do we actually need to allocate new blocks?
        if (lengthB <= prev_blocks.length * blockSizeB) return prev_blocks;

        else {
            if (getFreeSpaceBytes() < lengthB) {
                // TODO : Add error handling
            } else {

                for (; index < blocksAllocated.length; index++) {
                    int blockIndex = getFirstAvailableContentBlock();
                    blocksAllocated[index] = blockIndex;
                    setBlockUsed(blockIndex);
                }
            }
            return blocksAllocated;
        }
    }


     // FILE OPERATIONS //

    public void saveFile(String fileName, byte[] fileMemory) {
        try {
            // if the file already exists we just rewrite the inode entry contents and write the file to the already allocated blocks
            int fileInodeEntry = getFileInodeAddress(fileName);

            if (fileInodeEntry != -1){
                System.out.println("This file already exists. overwriting content");
                diskFile.seek(fileInodeEntry);
                diskFile.skipBytes(2);

                int size = fileMemory.length;
                int blocksUsedCount = diskFile.readByte();
                int[] blocksUsed = new int[blocksUsedCount];
                for(int i = 0; i < blocksUsedCount; i++) blocksUsed[i] = addressToBlock(diskFile.readInt());
                int[] newBlocks = allocateBlocksToFile(fileMemory.length, blocksUsed);

                overwriteInodeEntry(fileInodeEntry, size, newBlocks.length, newBlocks );

                diskFile.seek( blockToAddress(newBlocks[0]) );
                int blockIndex = 0, currentByte = 0;
                for(byte b : fileMemory){
                    if (currentByte >= blockSizeB){
                        blockIndex++;
                        diskFile.seek( blockToAddress( newBlocks[blockIndex] ) );
                        currentByte = 0;
                    }
                    diskFile.writeByte(b);
                    currentByte++;
                }
            }
            else {// otherwise allocate new blocks and create a new inode entry
                int[] blocks = allocateBlocksToFile(fileMemory.length);
                createInodeTableEntry(fileName, fileMemory.length, blocks.length, blocks);
                int blockIndex = 0;
                int currentByte = 0;
                diskFile.seek(blockToAddress(blocks[0]));

                for (byte b : fileMemory) {
                    if (currentByte >= blockSizeB) {
                        blockIndex++;
                        diskFile.seek(blockToAddress(blocks[blockIndex]));
                        currentByte = 0;
                    }
                    diskFile.writeByte(b);
                    currentByte++;
                }
            }
           // System.out.println("File saved successfully.");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void appendFile(String fileName, byte[] fileMemory){
        try{
            int inodePos = getFileInodeAddress(fileName);
            if (inodePos == -1) saveFile(fileName, fileMemory);
            else{
                diskFile.seek(inodePos);
                int oldSize = diskFile.readShort();
                int oldBlockCount = diskFile.readByte();
                int[] oldBlocks = new int[oldBlockCount];
                for(int i = 0; i < oldBlocks.length; i++) oldBlocks[i] = addressToBlock(diskFile.readInt());

                byte[] fileContent = readFile(fileName);

                // There's probably a way to track the file offset and append only new data. but I'm too lazy ATM.
                int newSize = fileContent.length + fileMemory.length;
                int[] newBlocks = allocateBlocksToFile(newSize, oldBlocks);

                byte[] newData = new byte[newSize];

                overwriteInodeEntry(inodePos, newSize, newBlocks.length, newBlocks);

                System.arraycopy(fileContent, 0, newData, 0, fileContent.length);
                System.arraycopy(fileMemory, 0, newData, fileContent.length, fileMemory.length);


                int currentByte = 0, blockIndex = 0;
                diskFile.seek( blockToAddress( newBlocks[0] ) );

                for(byte b : newData) {
                    if (currentByte >= blockSizeB) {
                        blockIndex++;
                        diskFile.seek( blockToAddress( newBlocks[blockIndex] ) );
                        currentByte = 0;
                    }
                    diskFile.writeByte(b);
                    currentByte++;
                }
            }
        }
        catch (Exception e) {throw new RuntimeException(e);}
    }

    public byte[] readFile(String fileName) {

        try {
            int inodeAddress = getFileInodeAddress(fileName);
            if (inodeAddress == -1){
                System.out.printf("File '%s' not found!\n", fileName);
                Logger.addLog( String.format("File '%s' not found!", fileName) );
                return new byte[] {-1};
            }
            else {
                diskFile.seek(inodeAddress);
                //System.out.println("Reading the file inode address at : 0x" + Integer.toHexString(inodeAddress));
                Logger.addLog("Reading the file inode address at : 0x" + Integer.toHexString(inodeAddress));

                int fileLength = diskFile.readShort();
                int blockCount = diskFile.readByte();
                int[] blocksAddresses = new int[blockCount];
                for (int i = 0; i < blocksAddresses.length; i++) blocksAddresses[i] = diskFile.readInt();

                //System.out.printf("""
                  //              file info :
                    //            file name : %s
                      //          file size : %d
                        //        blocks allocated : %d
                          //      """, fileName,
                //        fileLength,
                  //      blockCount);

                //for (int i = 0; i < blocksAddresses.length; i++)
                  //  System.out.printf("Block #%d address : 0x%06X\n", i, blocksAddresses[i]);

                byte[] fileBytes = new byte[fileLength];
                diskFile.seek(blocksAddresses[0]);

                int bytesRead = 0;
                int blockIndex = 0;

                for (int i = 0; i < fileBytes.length; i++) {

                    if (bytesRead >= blockSizeB) {
                        blockIndex++;
                        diskFile.seek(blocksAddresses[blockIndex]);
                        bytesRead = 0;
                    }

                    fileBytes[i] = diskFile.readByte();
                    bytesRead++;
                }
                return fileBytes;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteFile(String fileName) {

        try {
            int inodeAddress = getFileInodeAddress(fileName);
            if (inodeAddress == -1) System.out.printf("file '%s' not found\n", fileName);

            else{
                // get the file info...
                diskFile.seek(inodeAddress);
                int size = diskFile.readShort();
                int blocks = diskFile.readByte();
                int[] blockAddresses = new int[blocks];

                for(int i = 0; i < blockAddresses.length; i++) blockAddresses[i] = diskFile.readInt();

                // delete the inode entry
                diskFile.seek(inodeAddress);
                while ( (diskFile.readByte() & 0xff) != INODE_ENTRY_END){
                    diskFile.seek( diskFile.getFilePointer() - 1 );
                    diskFile.writeByte(0x0);
                }


                // free the blocks allocated to the deleted file
                for (int blockAddress : blockAddresses) setBlockAvailable(addressToBlock(blockAddress));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getFreeSpaceBytes() throws IOException {
        int freeBlocks = 0;
        for(int i = 0; i < blockCount; i++) freeBlocks += !isBlockUsed(i) ? 1 : 0;
        return freeBlocks * blockSizeB;
    }


    public int addressToBlock(int address){return address / blockSizeB;}
    public int blockToAddress(int blockIndex) {return blockIndex * blockSizeB;}
}
