package com.ke.coding.service.filesystem.fat16xservice.filesystemservice;

import static com.ke.coding.api.enums.Constants.ATTRIBUTE_DIRECTORY_POS;
import static com.ke.coding.api.enums.Constants.BOOT_SECTOR_SIZE;
import static com.ke.coding.api.enums.Constants.BOOT_SECTOR_START;
import static com.ke.coding.api.enums.Constants.DATA_REGION_START;
import static com.ke.coding.api.enums.Constants.DIRECTORY_ENTRY_SIZE;
import static com.ke.coding.api.enums.Constants.FAT_NC_END_OF_FILE;
import static com.ke.coding.api.enums.Constants.FAT_SIZE;
import static com.ke.coding.api.enums.Constants.FAT_START;
import static com.ke.coding.api.enums.Constants.O_EXLOCK;
import static com.ke.coding.api.enums.Constants.O_SHLOCK;
import static com.ke.coding.api.enums.Constants.PATH_SPLIT;
import static com.ke.coding.api.enums.Constants.PER_CLUSTER_SECTOR;
import static com.ke.coding.api.enums.Constants.PER_SECTOR_BYTES;
import static com.ke.coding.api.enums.Constants.ROOT_DIRECTORY_SIZE;
import static com.ke.coding.api.enums.Constants.ROOT_DIRECTORY_START;
import static com.ke.coding.api.enums.Constants.ROOT_PATH;
import static com.ke.coding.api.enums.ErrorCodeEnum.FILE_HAS_OPEN;
import static com.ke.coding.api.enums.ErrorCodeEnum.NOT_RM_ROOT;
import static com.ke.coding.api.enums.ErrorCodeEnum.NO_SUCH_FILE_OR_DIRECTORY;
import static com.ke.coding.common.ArrayUtils.array2List;
import static com.ke.coding.common.ArrayUtils.list2Ary;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.ke.coding.api.dto.filesystem.fat16x.Fat16Fd;
import com.ke.coding.api.dto.filesystem.fat16x.Fat16xFileSystem;
import com.ke.coding.api.dto.filesystem.fat16x.bootregion.BootSector;
import com.ke.coding.api.dto.filesystem.fat16x.directoryregion.DirectoryEntry;
import com.ke.coding.api.dto.filesystem.fat16x.directoryregion.RootDirectoryRegion;
import com.ke.coding.api.dto.filesystem.fat16x.fatregion.FatRegion;
import com.ke.coding.api.exception.CodingException;
import com.ke.coding.common.ArrayUtils;
import com.ke.coding.service.disk.FileDisk;
import com.ke.coding.service.disk.IDisk;
import com.ke.coding.service.filesystem.fat16xservice.regionservice.DataClusterService;
import com.ke.coding.service.filesystem.fat16xservice.regionservice.DataRegionService;
import com.ke.coding.service.filesystem.fat16xservice.regionservice.FatRegionService;
import com.ke.coding.service.filesystem.fat16xservice.regionservice.RootDirectoryRegionService;
import com.ke.risk.safety.common.util.date.DateUtil;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jodd.io.FileUtil;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

/**
 * @author: xueyunlong001@ke.com
 * @time: 2023/3/23 14:27
 * @description:
 */
public class Fat16xSystemServiceImpl implements FileSystemService<Fat16Fd> {

	@Getter
	public static final Fat16xFileSystem fat16xFileSystem = new Fat16xFileSystem();

	DataRegionService dataRegionService;

	FatRegionService fatRegionService;

	RootDirectoryRegionService rootDirectoryRegionService;

	IDisk iDisk;
	String filePath;

	private final ConcurrentHashMap<String, Integer> fileLock = new ConcurrentHashMap<>();

	@SneakyThrows
	public Fat16xSystemServiceImpl() {
		filePath = StringUtils.isEmpty(System.getProperty("filePath")) ? "123" : System.getProperty("filePath");
		iDisk = new FileDisk(filePath);
		boolean existingFile = FileUtil.isExistingFile(new File(filePath));
		if (!existingFile) {
			File file = new File(filePath);
			file.createNewFile();
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			raf.setLength(1024L * 1024 * 1024 * 2);
			raf.close();
			BootSector bootSector = new BootSector();
			iDisk.writeSector(BOOT_SECTOR_START, bootSector.getData());
			fat16xFileSystem.setReservedRegion(bootSector);
		} else {
			fat16xFileSystem.setReservedRegion(new BootSector(iDisk.readSector(BOOT_SECTOR_START, BOOT_SECTOR_SIZE)));
		}
		dataRegionService = new DataRegionService(new DataClusterService(iDisk));
		fatRegionService = new FatRegionService(iDisk);
		rootDirectoryRegionService = new RootDirectoryRegionService(iDisk);

		fat16xFileSystem.setRootDirectoryRegion(new RootDirectoryRegion(iDisk.readSector(ROOT_DIRECTORY_START, ROOT_DIRECTORY_SIZE)));
		fat16xFileSystem.setFatRegion(new FatRegion(iDisk.readSector(FAT_START, FAT_SIZE)));
	}

	/**
	 * 找到根目录条目
	 *
	 * @param currentPath 当前路径
	 * @return {@link DirectoryEntry}
	 */
	private Fat16Fd findRootDirectoryEntry(String currentPath) {
		String[] split = currentPath.split(PATH_SPLIT);
		String path = split[1];
		DirectoryEntry directoryEntry = null;
		for (int i = 0; i < fat16xFileSystem.getRootDirectoryRegion().getDirectoryEntries().length; i++) {
			if (fat16xFileSystem.getRootDirectoryRegion().getDirectoryEntries()[i] != null && fat16xFileSystem.getRootDirectoryRegion()
				.getDirectoryEntries()[i].getFileName().equals(path)) {
				directoryEntry = fat16xFileSystem.getRootDirectoryRegion().getDirectoryEntries()[i];
				directoryEntry.setIndex(i);
				directoryEntry.setRootEntry(true);
				break;
			}
		}
		return new Fat16Fd(directoryEntry, currentPath);
	}

	/**
	 * 获取条目下所有信息
	 *
	 * @param fd 跳频
	 * @return {@link List}<{@link Fat16Fd}>
	 */
	@Override
	public List<Fat16Fd> list(Fat16Fd fd) {
		if (fd.isEmpty()) {
			return new ArrayList<>();
		}
		List<Fat16Fd> result = new ArrayList<>();
		if (fd.getDirectoryEntry().isRoot()) {
			for (DirectoryEntry directoryEntry : fat16xFileSystem.getRootDirectoryRegion().getDirectoryEntries()) {
				if (directoryEntry != null) {
					Fat16Fd fat16Fd = new Fat16Fd(directoryEntry, buildFileName(directoryEntry), directoryEntry.getFileSize(),
						DateUtil.getDateStr(new Date(directoryEntry.getLastAccessTimeStamp()), "yyyy/MM/dd"));
					result.add(fat16Fd);
				}
			}
		} else {
			//找到对应的全部目录data cluster
			int[] dataIndex = fat16xFileSystem.getFatRegion().allOfFileClusterIndex(fd.getDirectoryEntry().getStartingCluster());
			for (int index : dataIndex) {
				byte[] allData = dataRegionService.getClusterData(index);
				List<List<Byte>> lists = Lists.partition(array2List(allData), DIRECTORY_ENTRY_SIZE);
				for (List<Byte> bytes : lists) {
					if (ArrayUtils.isEmpty(bytes)) {
						break;
					} else {
						DirectoryEntry temp = new DirectoryEntry();
						System.arraycopy(list2Ary(bytes), 0, temp.getData(), 0, DIRECTORY_ENTRY_SIZE);
						Fat16Fd fat16Fd = new Fat16Fd(temp, buildFileName(temp), temp.getFileSize(),
							DateUtil.getDateStr(new Date(temp.getLastAccessTimeStamp()), "yyyy/MM/dd"));
						result.add(fat16Fd);
					}
				}
			}
		}
		return result;
	}

	private String buildFileName(DirectoryEntry directoryEntry) {
		String name;
		if (1 == directoryEntry.getAttribute(ATTRIBUTE_DIRECTORY_POS)) {
			name = "/" + directoryEntry.getFileName();
		} else {
			name = directoryEntry.getFileNameExtension().equals("   ") ? directoryEntry.getFileName() :
				directoryEntry.getFileName() + "." + directoryEntry.getFileNameExtension();
		}
		return name;
	}

	@Override
	public void mkdir(String filePath, boolean dir) {
		String[] split = filePath.split(PATH_SPLIT);
		String fileName = split[split.length - 1];
		String currentPath;
		if (split.length == 2) {
			currentPath = ROOT_PATH;
		} else {
			List<String> strings = Stream.of(split).collect(Collectors.toList());
			strings.remove("");
			strings.remove(strings.size() - 1);
			currentPath = ROOT_PATH + Joiner.on(PATH_SPLIT).join(strings);
		}
		DirectoryEntry newDirectoryEntry = dir ? DirectoryEntry.buildDir(fileName) : DirectoryEntry.buildFile(fileName);
		if (ROOT_PATH.equals(currentPath)) {
			rootDirectoryRegionService.save(fat16xFileSystem.getRootDirectoryRegion(), fat16xFileSystem.getRootDirectoryRegion().freeIndex(),
				newDirectoryEntry);
		} else {
			//数据区域数据保存
			Fat16Fd open = open(currentPath);
			if (open.isEmpty()) {
				throw new CodingException(NO_SUCH_FILE_OR_DIRECTORY);
			}
			initDirectoryEntry(open.getDirectoryEntry());
			int newEndOfFileCluster = dataRegionService.saveDir(newDirectoryEntry, open.getDirectoryEntry().getStartingCluster(), fat16xFileSystem);
			fatRegionService.relink(open.getDirectoryEntry().getStartingCluster(), newEndOfFileCluster, fat16xFileSystem.getFatRegion());
		}
	}

	@Override
	public void close(Fat16Fd fd) {
		if (fd != null && StringUtils.isNotBlank(fd.getFilePathName())) {
			fileLock.remove(fd.getFilePathName());
		}
	}

	@Override
	public void rm(Fat16Fd fd) {
		String[] splitPath = fd.getFilePathName().split(PATH_SPLIT);
		if (splitPath.length == 0) {
			throw new CodingException(NOT_RM_ROOT);
		} else if (splitPath.length == 2) {
			rootDirectoryRegionService.rm(fat16xFileSystem.getRootDirectoryRegion(), fd.getDirectoryEntry());
		} else {
			//清除数据区域
			if (fd.getDirectoryEntry().getStartingCluster() != 0) {
				int[] index = fat16xFileSystem.getFatRegion().allOfFileClusterIndex(fd.getDirectoryEntry().getStartingCluster());
				for (int i : index) {
					fat16xFileSystem.getFatRegion().rmFat(i);
					dataRegionService.rmCluster(i);
				}
			}
			//清除目录本身
			fd.getDirectoryEntry().setData(new byte[DIRECTORY_ENTRY_SIZE]);
			persistDirectoryEntry(fd.getDirectoryEntry());

		}
	}

	@SneakyThrows
	@Override
	public void format() {
		fat16xFileSystem.format();
		filePath = StringUtils.isEmpty(System.getProperty("filePath")) ? "123" : System.getProperty("filePath");
		FileUtil.writeBytes(new File(filePath), new byte[0]);
		iDisk.writeSector(BOOT_SECTOR_START, fat16xFileSystem.getReservedRegion().getData());
	}

	DirectoryEntry initDirectoryEntry(DirectoryEntry directoryEntry) {
		if (directoryEntry == null) {
			directoryEntry = new DirectoryEntry();
			int firstFreeFat = fat16xFileSystem.getFatRegion().firstFreeFat();
			directoryEntry.setStartingCluster(firstFreeFat);
			iDisk.writeSector(DATA_REGION_START + firstFreeFat * PER_CLUSTER_SECTOR, directoryEntry.getData());
			fatRegionService.save(firstFreeFat, FAT_NC_END_OF_FILE, fat16xFileSystem.getFatRegion());
		} else if (directoryEntry.getStartingCluster() == 0) {
			directoryEntry = initDirectoryEntryCluster(directoryEntry);
		}
		return directoryEntry;
	}

	DirectoryEntry initDirectoryEntryCluster(DirectoryEntry directoryEntry) {
		if (directoryEntry.getStartingCluster() == 0) {
			allocate(directoryEntry);
			persistDirectoryEntry(directoryEntry);
		}
		return directoryEntry;
	}

	void allocate(DirectoryEntry directoryEntry) {
		int firstFreeFat = fat16xFileSystem.getFatRegion().firstFreeFat();
		directoryEntry.setStartingCluster(firstFreeFat);
		fatRegionService.save(firstFreeFat, FAT_NC_END_OF_FILE, fat16xFileSystem.getFatRegion());
	}

	void persistDirectoryEntry(DirectoryEntry directoryEntry) {
		if (directoryEntry.isRootEntry()) {
			rootDirectoryRegionService.update(fat16xFileSystem.getRootDirectoryRegion(), directoryEntry);
		} else {
			int sectorIdx = DATA_REGION_START + directoryEntry.getAtCluster() * PER_CLUSTER_SECTOR;
			// 17 * 32 = 544
			int i = directoryEntry.getIndex() * DIRECTORY_ENTRY_SIZE;
			// 544 / 512 = 1
			int sectorOffset = i / PER_SECTOR_BYTES;
			// 544 % 512 = 32
			int sectorOffsetBeginIndex = i % PER_SECTOR_BYTES;
			// 从第AtCluster个集群，偏移对应的sector数量，再偏移对应的entry数量
			iDisk.appendWriteSector(sectorIdx + sectorOffset, directoryEntry.getData(), sectorOffsetBeginIndex);
		}
	}

	@Override
	public Fat16Fd open(String fileName) {
		String[] splitPath = fileName.split(PATH_SPLIT);
		if (splitPath.length == 0) {
			DirectoryEntry directoryEntry = new DirectoryEntry();
			directoryEntry.setRoot(true);
			return new Fat16Fd(directoryEntry, fileName);
		} else if (splitPath.length == 2) {
			return findRootDirectoryEntry(fileName);
		} else {
			DirectoryEntry result = null;
			Fat16Fd fat16Fd = findRootDirectoryEntry(fileName);
			if (fat16Fd.isEmpty() || fat16Fd.getDirectoryEntry().getStartingCluster() == 0) {
				return new Fat16Fd(fileName);
			}
			int index = 2;
			while (index < splitPath.length) {
				List<Fat16Fd> fat16Fhs = list(fat16Fd);
				int offset = 0;
				for (Fat16Fd fat16Fh : fat16Fhs) {
					DirectoryEntry directoryEntry = fat16Fh.getDirectoryEntry();
					if (directoryEntry.getWholeFileName().equals(splitPath[index])) {
						if (index == splitPath.length - 1) {
							directoryEntry.setIndex(offset);
							directoryEntry.setAtCluster(fat16Fd.getDirectoryEntry().getStartingCluster());
							result = directoryEntry;
						}
						fat16Fd = new Fat16Fd(directoryEntry.getStartingCluster());
					}
					offset++;
				}
				index++;
			}
			return new Fat16Fd(result, fileName);
		}
	}

	@Override
	public Fat16Fd open(String fileName, int state) {
		if (fileLock.putIfAbsent(fileName, state) == null) {
			return open(fileName);
		} else if (fileLock.get(fileName) == O_EXLOCK) {
			throw new CodingException(FILE_HAS_OPEN);
		} else if (state == O_SHLOCK && fileLock.get(fileName) == O_SHLOCK) {
			return open(fileName);
		}
		return null;
	}

	@Override
	public int readFile(Fat16Fd fd, byte[] data) {
		//文件之前未写入过内容
		if (fd.getDirectoryEntry().getStartingCluster() == 0) {
			return -1;
		} else {
			if (fd.getPos() >= fd.getDirectoryEntry().getFileSize()) {
				return -1;
			}
			int[] index = fat16xFileSystem.getFatRegion().allOfFileClusterIndex(fd.getDirectoryEntry().getStartingCluster());
			byte[] dataBytes = new byte[(int) fd.getDirectoryEntry().getFileSize()];
			dataRegionService.getClustersData(index, dataBytes);
			fd.getDirectoryEntry().setLastAccessTimeStamp();
			int dataLength = Math.min((dataBytes.length - fd.getPos()), data.length);
			System.arraycopy(dataBytes, fd.getPos(), data, 0, dataLength);
			fd.setPos(fd.getPos() + data.length);
			return dataLength;
		}
	}

	@Override
	public void writeFile(Fat16Fd fd, byte[] dataBytes) {
		DirectoryEntry directoryEntry = initDirectoryEntryCluster(fd.getDirectoryEntry());
		int endOfFileCluster = fat16xFileSystem.getFatRegion().endOfFileCluster(directoryEntry.getStartingCluster());
		//集群剩余空间
		long endOfFileClusterUsedSpace = directoryEntry.getFileSize() == 0 ? 0 : (
			directoryEntry.getFileSize() % (PER_SECTOR_BYTES * PER_CLUSTER_SECTOR) == 0 ? PER_SECTOR_BYTES * PER_CLUSTER_SECTOR
				: directoryEntry.getFileSize() % (PER_SECTOR_BYTES * PER_CLUSTER_SECTOR)
		);
		int endOfFileClusterRemainSpace = (int) (PER_SECTOR_BYTES * PER_CLUSTER_SECTOR - endOfFileClusterUsedSpace);
		//文件size小于等于尾节点剩余空间，直接当前节点追加内容
		if (dataBytes.length <= endOfFileClusterRemainSpace) {
			dataRegionService.appendSaveFile(dataBytes, endOfFileCluster, (int) directoryEntry.getFileSize());
		} else {
			//文件size大于尾节点剩余空间
			byte[] appendSaveData = new byte[endOfFileClusterRemainSpace];
			System.arraycopy(dataBytes, 0, appendSaveData, 0, endOfFileClusterRemainSpace);
			//把当前节点装满
			dataRegionService.appendSaveFile(appendSaveData, endOfFileCluster, (int) directoryEntry.getFileSize());
			//申请新的数据集群列表保存剩余数据
			int[] fatArray = fatRegionService.allocateFatArray(dataBytes.length - endOfFileClusterRemainSpace, fat16xFileSystem.getFatRegion());
			//保存数据区域数据
			byte[] saveData = new byte[dataBytes.length - endOfFileClusterRemainSpace];
			System.arraycopy(dataBytes, endOfFileClusterRemainSpace, saveData, 0, dataBytes.length - endOfFileClusterRemainSpace);
			dataRegionService.saveFile(saveData, fatArray);
			//原有链路的末尾，指向新申请的链表首部
			fatRegionService.save(endOfFileCluster, String.format("%04x", fatArray[0]), fat16xFileSystem.getFatRegion());
		}
		//更新文件大小,时间
		directoryEntry.updateWriteInfo(directoryEntry.getFileSize() + dataBytes.length);
		persistDirectoryEntry(directoryEntry);
	}

}
