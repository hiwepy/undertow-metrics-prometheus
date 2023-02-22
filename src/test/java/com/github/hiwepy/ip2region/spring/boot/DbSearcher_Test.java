package com.github.hiwepy.ip2region.spring.boot;

import java.io.ByteArrayOutputStream;

import org.junit.Before;
import org.junit.Test;
import org.nutz.plugins.ip2region.DBReader;
import org.nutz.plugins.ip2region.DbConfig;
import org.nutz.plugins.ip2region.DbSearcher;
import org.nutz.plugins.ip2region.impl.ByteArrayDBReader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.util.FileCopyUtils;

public class DbSearcher_Test {

	DbSearcher searcher = null;

	@Before
	public void setUp()  throws Exception {
		DbConfig dbConfig = new DbConfig(8192);
		dbConfig.setIndexBlockSize(4096);

		// reader = new RandomAccessFileDBReader(new RandomAccessFile(resource.getFile(), "r"));

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		FileCopyUtils.copy(this.getClass().getClassLoader().getResourceAsStream("ip2region_new.db"), output);
		DBReader reader = new ByteArrayDBReader(output.toByteArray());

		searcher = new DbSearcher(dbConfig, reader);
	}

	@Test
	public void searcherTest() throws Exception {


		System.out.println(searcher.btreeSearch(1610329044 ).getRegion());
		System.out.println(searcher.binarySearch("114.124.146.103").getRegion());
		System.out.println(searcher.memorySearch("114.124.146.103").getRegion());
		/**
		 * for (int i = 1; i < 255; i++) {
		 *
		 * try { System.out.println(searcher.binarySearch("115.204.25." + i)); } catch
		 * (IOException e) { e.printStackTrace(); }
		 *
		 * }
		 */

	}

}
