package com.ke.coding.service.command.impl;

import static com.ke.coding.api.enums.Constants.ATTRIBUTE_DIRECTORY_POS;
import static com.ke.coding.api.enums.Constants.O_EXLOCK;
import static com.ke.coding.api.enums.ErrorCodeEnum.ACTION_ERROR;
import static com.ke.coding.api.enums.ErrorCodeEnum.RM_ERROR;
import static com.ke.coding.api.enums.ErrorCodeEnum.SYSTEM_SUCCESS;

import com.ke.coding.api.dto.filesystem.fat16x.Fat16Fd;
import com.ke.coding.api.exception.CodingException;
import com.ke.coding.service.command.AbstractAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.SneakyThrows;

/**
 * @author: xueyunlong001@ke.com
 * @time: 2023/4/25 16:25
 * @description:
 */
public class RmAction extends AbstractAction {

	@SneakyThrows
	@Override
	public void run() {
		byte[] input = readIn();
		String originData = new String(input);
		String[] s1 = originData.split(" ");
		if (s1.length != 2) {
			err.write(ACTION_ERROR.message().getBytes(StandardCharsets.UTF_8));
		}

		//step: 文件名，文件后缀长度限制
		String delDir = s1[1];
		String filePathName = buildFilePathName(delDir);
		Fat16Fd fd = null;
		try {
			fd = fileSystemService.open(filePathName, O_EXLOCK);
			if (fd != null && !fd.isEmpty()) {
				//文件直接删除
				if (1 != fd.getDirectoryEntry().getAttribute(ATTRIBUTE_DIRECTORY_POS)) {
					fileSystemService.rm(fd);
					out.write(SYSTEM_SUCCESS.message().getBytes(StandardCharsets.UTF_8));
				}else {
					//空目录直接删除
					if (fd.getDirectoryEntry().getStartingCluster() == 0 || fileSystemService.list(fd).isEmpty()){
						fileSystemService.rm(fd);
						out.write(SYSTEM_SUCCESS.message().getBytes(StandardCharsets.UTF_8));
						//非空目录无法删除
					}else {
						out.write(RM_ERROR.message().getBytes(StandardCharsets.UTF_8));
					}
				}
			}

		} catch (CodingException e) {
			err.write(e.getErrorCode().message().getBytes(StandardCharsets.UTF_8));
		} finally {
			fileSystemService.close(fd);
		}

	}
}
