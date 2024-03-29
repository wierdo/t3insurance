package uk.ac.abdn.t3.model;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class DataAnalyzer {
	static String agent_resource=ProvTrack.bbox_ns+"BboxServer";
	double basic_rate=30;
	public static boolean SHARE_DATA=false;
      
	
	int SPEED=50;
	double SPEED_RATE=0.002;
	double LOW_TURN_RATE=0.001;
	double MEDIUM_TURN_RATE=0.003;
	double HIGH_TURN_RATE=0.01;
	double LOW_BRAKING_RATE=0.001;
	double MEDIUM_BRAKING_RATE=0.003;
	double HIGH_BRAKING_RATE=0.001;
	
	int ENGINE_OK=0;
	int ENGINE_POOR=1;
	int ENGINE_VERY_POOR=2;
	boolean SEND=false;
	
	DB db=DB.getDB();

   String act;
   String usage;
   String provid;
	public void calculatePremium(Client u){	
		int low_turns=0 ;     // 1.001; %
		int medium_turns=0;   // 1.003; %
		int high_turns=0;     //1.01;   %
		
		int low_braking=0;    //1.001;
		int medium_braking=0;  //1.003;
		int high_braking=0;   //1.01;
		
		int speeding=0; 
		
		act=ProvTrack.bbox_ns+"CalculatePremium"+new Date().getTime();
	//	String actACC=ProvTrack.bbox_ns+"ActivityACC"+new Date().getTime();
		//String accData=ProvTrack.bbox_ns+"ACCEntity"+new Date().getTime();
		//String speedData=ProvTrack.bbox_ns+"SpeedEntity"+new Date().getTime();
		usage=ProvTrack.bbox_ns+"Usage"+new Date().getTime();
		
		String deviceid=u.getDeviceid();
		//last ten minutes
		String query="SELECT * FROM data WHERE tsreceived>=NOW() - INTERVAL 10 MINUTE AND deviceid=?";
	try{
		if(DB.conn.isClosed()){
			DB.conn=DriverManager.getConnection(Configuration.url+Configuration.dbName,Configuration.userName,Configuration.password);
		}
		PreparedStatement p=DB.conn.prepareStatement(query);
		p.setString(1, deviceid);
		ResultSet rs=p.executeQuery();
		if (!rs.isBeforeFirst() ) {    
			 System.out.println("CAR SERVER:There have been no data collection for past 10 minutes."); 
			 SEND=false;
			}
		else{
			SEND=true;
		}
		boolean first=true;
		while(rs.next()){
			int cornering=rs.getInt("cornering_level");
			int braking=rs.getInt("braking_level");
			if(cornering==2){
				low_turns++;
			}
			if(cornering==3){
				medium_turns++;
			}
			if(cornering==4){
				high_turns++;
			}
			if(braking==2){
				low_braking++;
			}
			if(braking==3){
				medium_braking++;
				
			}
			if(braking==4){
				high_braking++;
			}
			int speed=rs.getInt("speed");
			if(speed>SPEED){
				speeding++;
			}
			if(first){
				first=false;
			provid=rs.getString("provid");
			}
		}
	
		if(provid.equals("")){return;}
		
	}
	catch(Exception e){
		e.printStackTrace();
	}
	
	//check total distance
		//check speeding
	if(SEND){
	double currentP=u.getCurrentPremium();
	double newP=((LOW_TURN_RATE*low_turns+MEDIUM_TURN_RATE*medium_turns+HIGH_TURN_RATE*high_turns+low_braking*LOW_BRAKING_RATE
			+medium_braking*MEDIUM_BRAKING_RATE+high_braking*HIGH_BRAKING_RATE+speeding*SPEED_RATE)*currentP)+currentP;
	long atTime=new Date().getTime();
	
	
	
	//share data for response --location --
	
	String rawACCEnt=ProvTrack.bbox_ns+"Acc"+provid;
	//String rawLocation=ProvTrack.bbox_ns+"Location"+provid;
	String speed=ProvTrack.bbox_ns+"Speed"+provid;
	
	
	
	
//	ProvTrack.addStatement(actACC+" "+ProvTrack.type+ProvTrack.Activity);
//	ProvTrack.addStatement(actACC+" "+ProvTrack.wasAssociatedWith + agent_resource);
//	ProvTrack.addStatement(accData+" "+ProvTrack.wasGeneratedBy + actACC);
//	ProvTrack.addStatement(speedData+" "+ProvTrack.wasGeneratedBy + actACC);
	
	ProvTrack.addStatement(act+" "+ProvTrack.type+ProvTrack.Activity);
	ProvTrack.addStatement(act+" "+ProvTrack.used+rawACCEnt);  //TODO get specific form simboxx
//	ProvTrack.addStatement(accData+" "+ProvTrack.type +ProvTrack.Entity);
//	ProvTrack.addStatement(accData+" "+ProvTrack.type +ProvTrack.PersonalData);
//	ProvTrack.addStatement(accData+" "+ProvTrack.description+"\\\"Acelerometer ranges\\\"^^xsd:string");

	
	ProvTrack.addStatement(act+" "+ProvTrack.used+speed);
//	ProvTrack.addStatement(speedData+" "+ProvTrack.type +ProvTrack.Entity);
//	ProvTrack.addStatement(speedData+" "+ProvTrack.type +ProvTrack.PersonalData);
//	ProvTrack.addStatement(speedData+" "+ProvTrack.description+"\\\"Speed\\\"^^xsd:string");
	
	ProvTrack.addStatement(usage+" "+ProvTrack.entity +speed);
	
	ProvTrack.addStatement(usage+" "+ProvTrack.type+ProvTrack.Usage);
	ProvTrack.addStatement(usage+" "+ProvTrack.purpose+"\\\"Using data to calculate premiums\\\"^^xsd:string");
	ProvTrack.addStatement(usage+" "+ProvTrack.entity +rawACCEnt);
	ProvTrack.addStatement(act+" "+ProvTrack.qualifiedUsage+ usage);
	ProvTrack.addStatement(act+" "+ProvTrack.wasAssociatedWith + agent_resource);
	
	if(SHARE_DATA){
		
	int performance=getPerformanceDataFromManufacturer(high_turns,high_braking,rawACCEnt);	
	
	if(performance==-1){
		System.out.println("No perf data available");
	}
	else if(performance==ENGINE_OK){
		System.out.println("Engine is OK");
	}
	else if(performance==ENGINE_POOR){
		newP*=1.05;
	}
	else if(performance==ENGINE_VERY_POOR){
		newP*=1.1;
		
	}
	
		
		
		
		
		
		
		
	}
	
	if(newP>currentP){
System.err.println("New premiuim bigger");
		String update="UPDATE clients SET premium=? WHERE id=?";
		String preEnt=ProvTrack.bbox_ns+"NewPremium"+new Date().getTime();
	ProvTrack.addStatement(preEnt +" "+ProvTrack.wasGeneratedBy+act);
	ProvTrack.addStatement(preEnt+" "+ProvTrack.type +ProvTrack.Entity);
	ProvTrack.addStatement(preEnt+" "+ProvTrack.type +ProvTrack.PersonalData);
	ProvTrack.addStatement(preEnt+" "+ProvTrack.type +ProvTrack.BillingData);
	ProvTrack.addStatement(preEnt+" "+ProvTrack.description+"\\\"Insurance premium\\\"^^xsd:string");
	
	
	
	
		try{
		PreparedStatement p=DB.conn.prepareStatement(update);
		p.setDouble(1, newP);
		p.setInt(2, u.getId());
		int i=p.executeUpdate();
		if(i>0){
			String message="Dear "+u.getLast_name()+",\n\n We are writing to you as your insurance premium has been increased due to your past "+
					"driving behaviour.\n\n Old Premium:\t£"+Math.round(currentP * 100.0) / 100.0 +"\n Your new Premium:\t£"+Math.round(newP * 100.0) / 100.0+"\n\nKind Regards\nINSUREBBOX LTD Financial Team";
			new SendMailTLS().sendMail(u.getEmail(), message);
			System.err.println("email sent...");
		}
		}
		catch(Exception e){e.printStackTrace();}
	
	
		
		
	}
	ProvTrack.sendProv();    //send prov to T3
	}
	else{
		System.err.println("No data generated in last ten minutes nothing to check");
		System.out.println("No data generated in last ten minutes nothing to check");
	}
	
	

		
	}
	

	public int getPerformanceDataFromManufacturer(int highTurns,int highBraking, String provDataRef){
		
		CloseableHttpClient httpClient = HttpClientBuilder.create().build();

		String body="{\"highturns\":\""+highTurns+"\",\"highbraking\":\""+highBraking+"\",\"prov\":\""+provDataRef+"\"}";
		
		try {
		    HttpPost request = new HttpPost("http://t3.abdn.ac.uk:8080/carmanufacturer/performance/");
		    StringEntity params = new StringEntity(body);
		    request.addHeader("content-type", "application/json");
		    request.setEntity(params);
		   HttpResponse resp= httpClient.execute(request);
		  System.out.println("StatusCode: "+ resp.getStatusLine().getStatusCode());
		  BufferedReader reader = new BufferedReader(new InputStreamReader(resp.getEntity().getContent(), "UTF-8"));
		  String json = reader.readLine();
		  JSONObject perfData = new JSONObject(json);
		 
		  int p=perfData.getInt("performance");
		 String genData=perfData.getString("provdata");
		 System.out.println("Prov Data from Manufacturer:"+genData);
		
		 //add to prov
		 ProvTrack.addStatement(act+" "+ProvTrack.used+genData);
		ProvTrack.addStatement(usage+" "+ProvTrack.entity +genData);
		 
		 return p;
		  
		} catch (Exception ex) {
		   ex.printStackTrace();
		} finally {
		   // httpClient.close();
		}
		
		
		
		
		return -1;
		
	}
}
