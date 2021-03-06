package org.apache.sling.mailarchiveserver.impl;

import static org.apache.sling.mailarchiveserver.util.MessageFieldName.*;
import static org.apache.sling.mailarchiveserver.impl.MessageStoreImpl.getDomainNodeName;
import static org.apache.sling.mailarchiveserver.impl.MessageStoreImpl.getListNodeName;
import static org.apache.sling.mailarchiveserver.impl.MessageStoreImpl.makeJcrFriendly;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.Field;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

public class MessageStoreImplRepositoryTestUtil {

	private static final String TEST_FILE_FIELD_SEPARATOR = " : ";
	
	/**
	 * Code is taken from http://svn.apache.org/repos/asf/sling/trunk/launchpad/test-services/src/main/java/org/apache/sling/launchpad/testservices/serversidetests/WriteableResourcesTest.java
	 */
	static void assertValueMap(ValueMap m, String ... keyValue) {
		assertNotNull("Expecting non-null ValueMap", m);
		for(int i=0 ; i< keyValue.length; i+=2) {
			final String key = keyValue[i];
			final String value = keyValue[i+1];
			assertEquals("Expecting " + key + "=" + value, value, m.get(key, String.class));
		}
	}

	static String readTextFile(File bodyFile) throws FileNotFoundException {
		Scanner sc = null;
		try {
			sc = new Scanner(bodyFile);
			String expectedBody = ""; 
			while (sc.hasNextLine()) {
				expectedBody += sc.nextLine() + "\n";
			}
			expectedBody = expectedBody.substring(0, expectedBody.length()-1);
			return expectedBody;
		} finally {
			if (sc != null) {
				sc.close();
			}
		}
	}

	static String specialPathFromFilePath(String fpath, String suffix, String ext) {
		int dotIdx = fpath.lastIndexOf(".");
		String bodyPath = fpath.substring(0, dotIdx) + suffix + "." + ext; 
		return bodyPath;
	}
	
	static String specialPathFromFilePath(String fpath, String suffix) {
		int dotIdx = fpath.lastIndexOf(".");
		return specialPathFromFilePath(fpath, suffix, fpath.substring(dotIdx + 1));
	}

	static String getResourcePath(Message msg, MessageStoreImpl store) {
		final Header hdr = msg.getHeader();
		final String listIdRaw = hdr.getField("List-Id").getBody();
		final String listId = listIdRaw.substring(1, listIdRaw.length()-1); // remove < and >

		String msgId;
		final Field msgIdField = hdr.getField("Message-ID");
		if (msgIdField != null) {
			msgId = msgIdField.getBody();
			msgId = msgId.substring(1, msgId.length()-1);
		} else {
			msgId = Integer.toHexString(hdr.getField("Date").hashCode());
		}
		msgId = makeJcrFriendly(msgId);
		
		String subject = null;
		final Field subjectField = hdr.getField("Subject");
		if (subjectField != null) {
			subject = subjectField.getBody();
		}
		
		String threadPath = store.threadKeyGen.getThreadKey(subject);
		String path = store.archivePath + getDomainNodeName(listId) + "/" + getListNodeName(listId) +
				"/" + threadPath + "/" + msgId;
		return path;
	}

	static void assertHeaders(ModifiableValueMap m, File headersFile) throws FileNotFoundException {
		Map<String, List<String>> expectedHeaders = new HashMap<String, List<String>>();
		Scanner sc = new Scanner(headersFile);
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			if (line.startsWith("//")) 
				continue;
			String[] colon = line.split(TEST_FILE_FIELD_SEPARATOR);
			String header = colon[0];
			String value = colon[1];
			List<String> values;
			if ((values = expectedHeaders.get(header)) == null) {
				values = new ArrayList<String>();
				expectedHeaders.put(header, values);
			}
			values.add(value);
		}
		sc.close();
	
		assertEquals("Expecting same number of headers", expectedHeaders.keySet().size(), m.keySet().size()-1); // -1 for Body, should be no htmlBody
	
		for (String header : m.keySet()) {
			if (!header.equals(PLAIN_BODY) && !header.equals(HTML_BODY)) {
				for (String value : m.get(header, String.class).split(MessageStoreImpl.FIELD_SEPARATOR)) {
					List<String> expectedHeader = expectedHeaders.get(header);
					assertNotNull("Expecting header \""+header+"\" to exist", expectedHeader);
                    assertTrue("Expecting same value of header \""+header+"\"", expectedHeader.contains(value));
				}
			}
		}
	}

	static void assertLayer(Resource root, List<String> types, int depth) {
		for (Resource child : root.getChildren()) {
			final ModifiableValueMap m = child.adaptTo(ModifiableValueMap.class);
			if (m.keySet().contains(MessageStoreImplRepositoryTest.TEST_RT_KEY)) {
				String type = m.get(MessageStoreImplRepositoryTest.TEST_RT_KEY, String.class);
				assertEquals(String.format("Expecting %s to have %s type", child.getPath(), types.get(depth)), types.get(depth), type);
			}
			if (child.getChildren().iterator().hasNext()) {
				assertLayer(child, types, depth+1);
			}
		}
	
	}

}
