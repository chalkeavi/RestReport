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
	public static List<String> PROCESSED_JSON_NODE_LIST = new ArrayList<>(List.of("IMPORT", "EXPORT"));
	public static List<String> IGNORED_JSON_NODE_LIST = new ArrayList<>(List.of("AT", "RE"));

	public static void main(String[] args) throws ParseException, IOException, java.text.ParseException {

		getdataFromgson(getJsonFile());
		// getdataFromgson(getJsonDataFromServer());
	}

	public static String getJsonDataFromServer()
			throws IOException, ParseException, java.text.ParseException{

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

	public static void processRawData(List<Object> rawData) throws FileNotFoundException, IOException {
		JsonParser parser = new JsonParser();
		Map<String, List<String>> finalMap = new HashMap<String, List<String>>();
		for (Object object : rawData) {
			JsonElement jsonTree = parser.parse(((JsonElement) object).getAsJsonPrimitive().getAsString());
			JsonObject jsonObject = jsonTree.getAsJsonObject();
			String type = jsonObject.get("type").toString().toUpperCase().replaceAll("[^a-zA-Z0-9_-]", "");
			if (PROCESSED_JSON_NODE_LIST.contains(type.toString())) {
				// System.out.println(jsonObject);
				JsonElement jsonElement = jsonObject.get("types");
				if (jsonElement.isJsonObject()) {
					JsonObject jObject = jsonElement.getAsJsonObject();
					Set<Map.Entry<String, JsonElement>> entries = jObject.entrySet();
					for (Map.Entry<String, JsonElement> entry : entries) {
						// System.out.println(entry.getKey());
						if (!IGNORED_JSON_NODE_LIST.contains(entry.getKey())) {

							Set<Map.Entry<String, JsonElement>> data = entry.getValue().getAsJsonObject().entrySet();
							String assetType = "";
							for (Map.Entry<String, JsonElement> tree : data) {
								// System.err.println(tree.getKey());

								if (tree.getKey().toUpperCase().equalsIgnoreCase("TYPE")) {
									assetType = tree.getValue().toString().split(":")[0].replaceAll("[^a-zA-Z0-9_-]",
											"");

								}
								List<String> finalList = new ArrayList<String>();
								if (tree.getKey().toUpperCase().equalsIgnoreCase("PARENT")) {
									Set<Map.Entry<String, JsonElement>> parentData = tree.getValue().getAsJsonObject()
											.entrySet();
									for (Map.Entry<String, JsonElement> tree2 : parentData) {
										String[] values = tree2.getValue().toString().replaceAll("[^a-zA-Z0-9_,:-]", "")
												.split(",");
										Integer add = Integer.valueOf(values[0].split(":")[1]);
										Integer remove = Integer.valueOf(values[1].split(":")[1]);
										Integer update = Integer.valueOf(values[2].split(":")[1]);
										Integer total = add + remove + update;

										// String add = values
										finalList.add(
												tree2.getKey() + "~" + add + "~" + update + "~" + remove + "~" + total);

									}
								}
								if (assetType != null && !assetType.equalsIgnoreCase("")) {
									finalMap.put(assetType, finalList);
								}
							}
						}
					}
				}
			}
		}
		printRecords(finalMap);
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

				//System.out.println(detail);
				int columnCount = 0;
				Object[] values = detail.split("~");
				Row row = sheet.createRow(++rowCount);
				Cell cell12 = row.createCell(0);
				cell12.setCellValue(data.getKey());
				for (Object value : values) {
					Cell cell = row.createCell(++columnCount);
					if (value instanceof String) {
						cell.setCellValue((String) value);
					} else if (value instanceof Integer) {
						cell.setCellValue((Integer) value);
					}
				}
			}

		}

		try (FileOutputStream outputStream = new FileOutputStream("Report.xls")) {
			workbook.write(outputStream);
		}
	}

	public static void getdataFromgson(String file) throws ParseException, FileNotFoundException, IOException {
		JsonParser parser = new JsonParser();
		JsonElement jsonTree = parser.parse(file);
		// System.out.println("Value is " + jsonTree);
		JsonObject jsonObject = jsonTree.getAsJsonObject();
		JsonElement f2 = jsonObject.get("jobs");
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
						// System.out.println(detail.get(REQUIRED_JSON_NODE));
					}
				}
			}
		}
		processRawData(rawDetails);
	}
}
