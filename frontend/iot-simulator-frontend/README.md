# IotSimulatorFrontend

Architecture Flow:

Frontend → Simulator Backend → Supabase REST APIs → Supabase Database

1. Email dropdown - Choose the elderly person (already done)
2. Device name dropdown - Choose the device_name for that person (this should show device names like "KT001", not device_type)
3. Data type dropdown - Choose the data_type (heart_rate, temperature, etc.)
4. Value/Range - Set range or fixed value based on data_type
5. Simulate button - Send data to the API

The symbIOT project is already the main project with the Supabase database and
APIs. The symbIOTSimulator should just call those existing APIs instead of connecting directly to the database.

So the flow should be:
1. Frontend calls Simulator Backend API
2. Simulator Backend calls symbIOT Backend APIs to get data
3. Simulator Backend returns the data to Frontend

The simulator backend is now a lightweight facade that:
- Gets devices from Supabase (same data as symbiot-care-peace uses)
- Gets data types for each device from Supabase
- Provides data ranges and units (hardcoded in memory)
- Returns everything to the frontend


This project was generated with [Angular CLI](https://github.com/angular/angular-cli) version 15.2.11.


IOT health Monitoring for elderly care -

I have a tracking project 'symbIOT' that tracks user through notifications that comes from sensors that are in the user home.
It is like I am an observer (care taker) who is observing a person far away.
I registered and logged in into the system through UI and I added my old parent (whom I will observe) who is another registered member in the system.
The system is capable of Automatic detection of daily routines, movement patterns and activity levels to spot changes early.
There are Intelligent notifications for falls, missed medications, irregular vitals, or unusual behavior patterns.
This system is already running but the data is fake data which is stored in the database.

I want to control this fake data using separate project that will have its own config UI and backend.
The backend will work as a simulator that will act based on config driven from UI.
So I created an application with backend (symbIOTSimulator) in java and frontend UI (symbIOTSimulatorFrontend) in angular.

The UI should have a list of emails to choose from dropdown (API will give this list).
Here is the API that gives that emails list -
GET https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/profiles

Use this request header in the GET call -
apikey = eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndpeWZjdnlwZWlmYmRhcW5mZ3JyIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTk4MDEyNDYsImV4cCI6MjA3NTM3NzI0Nn0._xA81aVu_on-OL-axvskm4aCCywkIZliIiXGFu4ey2c

The response will be like this -

[
{
"id": "971c7b95-ea5d-4ac2-a42f-8fa3c7cb6a0d",
"email": "yogesh.sharma@faberwork.com",
"full_name": "John Smith",
"phone": null,
"avatar_url": null,
"created_at": "2025-10-07T15:13:03.911094+00:00",
"updated_at": "2025-10-07T15:13:03.911094+00:00"
},
{
"id": "bb9c3677-e7e8-410b-8521-bd2b577195ce",
"email": "sharma.yogesh.1234@gmail.com",
"full_name": "Dave Smith",
"phone": null,
"avatar_url": null,
"created_at": "2025-10-07T15:24:26.533193+00:00",
"updated_at": "2025-10-07T15:24:26.533193+00:00"
}
]


Then for that chosen email, we will simulate data as this is the one which we will observe.
We will then provide the list of device_name in the dropdown (API will give this list. API yet to create).


At this point, elderly person is choosen and its device_name is choosen.
Now, we will give option to select data_type from the drop down (API will give this list. API yet to create).
Now, we will give option for the range or fix value based on the previously choosen values.
Note that options should have valid ranges. So verify the suitable range based on data_type.
For example - if data type is temperature then see what range we can give.

Then finally we will give simulate button.
User will click simulate button and the POST API in a tracking project will get called that will take selected parameters as request payload and generate data.
Here is POST API -

https://wiyfcvypeifbdaqnfgrr.supabase.co/functions/v1/device-ingest

Request payload with Bearer token: symbiot_ec287b05b25fc837a603a850c -

{
"device_id": "KT001",
"data_type": "heart_rate",
"value": { "bpm": 72 },
"unit": "bpm"
}

Response will be 201 created.

The tables in the tracking project are as follows -


devices, device_data, profiles


Also let me tell you one more thing. Again its just for better way to handle things.
So I think, the UI should have settings for a given user such that if user email is chosen and its corresponding other fields are chosen and then set once then next time,
it shouldn't be required to reset the same things again unless user changes it explicitly.
So its a kind of settings. Therefore, once user is chosen and its setting is set then next time simulate button is enough to send the data.
