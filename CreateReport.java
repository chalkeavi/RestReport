import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class CreateReport {

	public static String REST_URL = "";
	public static String USERNAME = "";
	public static String PASSWORD = "";
	public static String FIRST_ELEMENT_OF_RESPONSE = "jobs";
	public static String SAMPLE_JSON_FILE_LOCATION = "app.json";
	public static String DATE_SELECTION_VALUE = "startDate";
	public static String REQUIRED_JSON_NODE = "message";
	public static List<String> PROCESSED_JSON_NODE_LIST = new ArrayList<String>(Arrays.asList("IMPORT", "EXPORT"));
	public static List<String> IGNORED_JSON_NODE_LIST = new ArrayList<>(List.of("AT", "RE"));

	public static void main(String[] args) throws ParseException, IOException, java.text.ParseException {

		getdataFromgson(getJsonFile());
		// getdataFromgson(getJsonDataFromServer());
	}

	public static String getJsonDataFromServer()
			throws IOException, ParseException, java.text.ParseException, org.json.simple.parser.ParseException {

		// URL url = new URL("https://jsonplaceholder.typicode.com/todos/1");
		URL url = new URL(REST_URL);

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		String lines = "";
		try {
			// Set the username and password
			String auth = USERNAME + ":" + PASSWORD;
			byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8));
			String authHeaderValue = "Basic " + new String(encodedAuth);
			conn.setRequestProperty("Authorization", authHeaderValue);
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
			}

			BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

			String output;

			while ((output = br.readLine()) != null) {
				lines += output;
				System.out.println(output);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			conn.disconnect();
		}

		return lines;
	}

	public static String getJsonFile() throws IOException {
		FileReader f = new FileReader(SAMPLE_JSON_FILE_LOCATION);
		String lines = "";
		BufferedReader br = new BufferedReader(f);

		String output;
		System.out.println("Output from Server .... \n");

		while ((output = br.readLine()) != null) {
			lines += output;
			// System.out.println(output);
		}
		br.close();
		return lines;
	}

	public static boolean isThisPreviousdayDate(Long date) throws java.text.ParseException {
		DateFormat format = new SimpleDateFormat("yyyy-MM-dd");
		// Get Date from json Object
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(date);
		// Get Previous Day Date
		Calendar previousDay = Calendar.getInstance();
		previousDay.add(Calendar.DATE, -1);

		Date jsonDate = format.parse(format.format(cal.getTime()));
		Date previousDayDate = format.parse(format.format(previousDay.getTime()));
		// System.out.println("Previous :" + previousDayDate);
		// System.out.println("jsonDate :" + jsonDate);
		// Compare if both are same
		if (jsonDate.compareTo(previousDayDate) == 0) {
			return true;
		} else {
			return false;
		}

	}

	public static Set<Map.Entry<String, JsonElement>> getMapData(Map.Entry<String, JsonElement> entry) {
		return entry.getValue().getAsJsonObject().entrySet();
	}

	public static void processRawData(List<Object> rawData) throws FileNotFoundException, IOException {
		JsonParser parser = new JsonParser();
		Map<String, List<String>> finalMap = new HashMap<String, List<String>>();
		for (Object object : rawData) {
			if (isValidMessage(object)) {
				JsonElement jsonTree = parser.parse(((JsonElement) object).getAsJsonPrimitive().getAsString());
				JsonObject jsonObject = jsonTree.getAsJsonObject();
				String type = jsonObject.get("type").toString().toUpperCase().replaceAll("[^a-zA-Z0-9_-]", "");
				if (PROCESSED_JSON_NODE_LIST.contains(type.toString())) {
					JsonElement jsonElement = jsonObject.get("types");
					if (jsonElement != null) {
						if (jsonElement.isJsonObject()) {
							JsonObject jObject = jsonElement.getAsJsonObject();
							Set<Map.Entry<String, JsonElement>> entries = jObject.entrySet();
							for (Map.Entry<String, JsonElement> entry : entries) {
								if (!IGNORED_JSON_NODE_LIST.contains(entry.getKey())) {
									String assetType = "";
									for (Map.Entry<String, JsonElement> tree : getMapData(entry)) {
										if (tree.getKey().toUpperCase().equalsIgnoreCase("TYPE")) {
											assetType = tree.getValue().toString().split(":")[0]
													.replaceAll("[^a-zA-Z0-9_-]", "");

										}
										List<String> finalList = new ArrayList<String>();
										if (tree.getKey().toUpperCase().equalsIgnoreCase("PARENT")) {
											for (Map.Entry<String, JsonElement> tree2 : getMapData(tree)) {
												String[] values = tree2.getValue().toString()
														.replaceAll("[^a-zA-Z0-9_,:-]", "").split(",");
												Integer add = Integer.valueOf(values[0].split(":")[1]);
												Integer remove = Integer.valueOf(values[1].split(":")[1]);
												Integer update = Integer.valueOf(values[2].split(":")[1]);
												Integer total = add + remove + update;
												finalList.add(tree2.getKey() + "~" + add + "~" + update + "~" + remove
														+ "~" + total);
											}
										}
										if (assetType != null && !assetType.equalsIgnoreCase("")) {
											if (null != finalMap.get(assetType)) {

												finalMap.get(assetType).addAll(finalList);
											} else {
												finalMap.put(assetType, finalList);
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		printRecords(refineDatas(finalMap));
	}

	public static Map<String, List<String>> refineDatas(Map<String, List<String>> finalMap) {

		for (Map.Entry<String, List<String>> map : finalMap.entrySet()) {
			for (int i = 0; i < map.getValue().size(); i++) {
				String str = map.getValue().get(i);
				if (map.getValue().size() > 1) {
					for (int j = 0; j < map.getValue().size(); j++) {
						String str1 = map.getValue().get(j);
						if (str.equalsIgnoreCase(str1) && i == j) {
							continue;
						}
						if (str.split("~")[0].equalsIgnoreCase(str1.split("~")[0])) {
							{
								Integer add = Integer.parseInt(str.split("~")[1])
										+ Integer.parseInt(str1.split("~")[1]);
								Integer update = Integer.parseInt(str.split("~")[2])
										+ Integer.parseInt(str1.split("~")[2]);
								Integer remove = Integer.parseInt(str.split("~")[3])
										+ Integer.parseInt(str1.split("~")[3]);
								Integer total = Integer.parseInt(str.split("~")[4])
										+ Integer.parseInt(str1.split("~")[4]);
								map.getValue().set(map.getValue().indexOf(str),
										str.split("~")[0] + "~" + add + "~" + update + "~" + remove + "~" + total);
								str = str.split("~")[0] + "~" + add + "~" + update + "~" + remove + "~" + total;
								map.getValue().remove(map.getValue().get(j));
								j--;
							}
						}
					}
				}
			}
		}
		return finalMap;

	}

	public static boolean isValidMessage(Object message) {

		if (message != null && message.toString().contains("type"))
			return true;
		else
			return false;
	}

	public static void printRecords(Map<String, List<String>> finalData) throws FileNotFoundException, IOException {
		HSSFWorkbook workbook = new HSSFWorkbook();
		HSSFSheet sheet = workbook.createSheet("Report");

		int rowCount = 0;

		for (Map.Entry<String, List<String>> data : finalData.entrySet()) {

			List<String> details = data.getValue();
			Row row1 = sheet.createRow(0);
			Cell cell1 = row1.createCell(0);
			cell1.setCellValue("Asset Type");

			Cell cell2 = row1.createCell(1);
			cell2.setCellValue("Domain");

			Cell cell3 = row1.createCell(2);
			cell3.setCellValue("Creation");

			Cell cell4 = row1.createCell(3);
			cell4.setCellValue("Modification");

			Cell cell5 = row1.createCell(4);
			cell5.setCellValue("Obsolute");

			Cell cell6 = row1.createCell(5);
			cell6.setCellValue("Total");

			for (String detail : details) {

				// System.out.println(detail);
				int columnCount = 0;
				Object[] values = detail.split("~");
				Row row = sheet.createRow(++rowCount);
				Cell cell12 = row.createCell(0);
				cell12.setCellValue(data.getKey());
				for (Object value : values) {
					Cell cell = row.createCell(++columnCount);

					cell.setCellValue(value.toString());
				}
			}

		}

		try (FileOutputStream outputStream = new FileOutputStream("Report.xls")) {
			workbook.write(outputStream);
			System.out.println("Report has been generated....");
		}
	}

	@SuppressWarnings("unused")
	public static void getdataFromgson(String file) throws ParseException, FileNotFoundException, IOException {
		// try {
		JsonParser parser = new JsonParser();
		JsonElement jsonTree = parser.parse(file);
		// System.out.println("Value is " + jsonTree);
		JsonObject jsonObject = jsonTree.getAsJsonObject();
		JsonElement f2 = jsonObject.get(FIRST_ELEMENT_OF_RESPONSE);
		List<Object> rawDetails = new ArrayList<Object>();
		if (f2.isJsonArray()) {
			JsonArray details = f2.getAsJsonArray();
			// System.out.println(details);
			for (JsonElement obj : details) {
				if (obj.isJsonObject()) {
					JsonObject detail = obj.getAsJsonObject();
					Long startDate = Long.parseLong(detail.get(DATE_SELECTION_VALUE).toString());
					if (isThisPreviousdayDate(startDate)) {
						rawDetails.add(detail.get(REQUIRED_JSON_NODE));
					}
				}
			}
		}
		if (!rawDetails.isEmpty())
			processRawData(rawDetails);
		else
			System.out.println("No Records Available for Current Date ....");

	}
}
