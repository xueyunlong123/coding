package com.ke.coding.service.stream.output;

import com.ke.coding.api.dto.filesystem.fat16x.Fat16Fd;
import com.ke.coding.service.filesystem.fat16xservice.filesystemservice.Fat16xSystemServiceImpl;
import com.ke.coding.service.filesystem.fat16xservice.filesystemservice.FileSystemService;
import java.io.IOException;
import lombok.SneakyThrows;

/**
 * @author: xueyunlong001@ke.com
 * @time: 2023/4/6 17:37
 * @description:
 */
public class Fat16xOutputStream extends AbstractFileOutputStream<Fat16Fd> {

	FileSystemService<Fat16Fd> fileSystemService = new Fat16xSystemServiceImpl();


	public Fat16xOutputStream(Fat16Fd fd) {
		super(fd);
	}

	@Override
	public void write(int b) throws IOException {

	}

	/**
	 * 写
	 *
	 * @param data 数据
	 */
	@SneakyThrows
	@Override
	public void write(byte[] data) {
		fileSystemService.writeFile(fd, data);
	}
}
