package com.ke.coding.service.stream.input;

import com.ke.coding.api.dto.filesystem.Fd;
import java.io.InputStream;
import lombok.Data;

/**
 * @author: xueyunlong001@ke.com
 * @time: 2023/4/4 11:30
 * @description:
 */
@Data
public abstract class AbstractInputStream<F extends Fd> extends InputStream {

	public F fd;

}
