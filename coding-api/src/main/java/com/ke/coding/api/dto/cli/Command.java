package com.ke.coding.api.dto.cli;

import java.util.List;
import lombok.Data;

/**
 * @author: xueyunlong001@ke.com
 * @time: 2023/3/1 11:36
 * @description:
 */
@Data
public class Command {

	private String option;

	private List<String> params;

	private String currentPath;

	public static Command build(String currentPath, List<String> params){
		Command command = new Command();
		command.setCurrentPath(currentPath);
		command.setParams(params);
		return command;
	}

}