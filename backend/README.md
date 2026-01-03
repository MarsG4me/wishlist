# WishList App - Backend



## Building process

### Environment

openjdk 25 2025-09-16 <br>
OpenJDK Runtime Environment (build 25+36-3489) <br>
OpenJDK 64-Bit Server VM (build 25+36-3489, mixed mode, sharing) <br>

### Start the build process:
```
mvn package spring-boot:repackage -e
```

## Error Codes
Each error a logged in user may encounter will be listed below, the errors will have two fields one which is a text for the user and another representing a code that can be used in the frontend for a localized message:

|error|errId|HTTP code|reason|
|:---|:---|:---|:---|
|User verification failed.|USER_VERIFICATION_FAILED|500|JWT could not be used to create/verify the user in the System |
|User can't create more groups.|USER_REACHED_GROUP_CREATION_LIMIT|403|User tries o  create another group but already created 5|
|Group not found.|GROUP_NOT_FOUND|404|A get request for a group returned nothing|
|User is not a member of that group.|NOT_A_GROUP_MEMBER|403|User tried to access some group info without beeing part of that group|
|Offset must be >= 0|INVALID_OFFSET|400|The provided offset is not valid and must be a number larger than 0|
|User is not an admin of that group.|NOT_A_GROUP_ADMIN|403|User tried to perform an action only a group admin can do|
|Request body is missing or malformed JSON.|MISSING_REQUEST_BODY|400|The request is missing a body or the body is not JSON|
|Can only be done by group owner.|NOT_THE_GROUP_OWNER|401|The action performed can only be done by the group owner, like managing admin permissions and deleting the group|
|Not possible for yourself.|CANT_DO_SELF|400|User tried to do something which would update the users state like updating the users admin state for a group, but that is an operation which can only be done for others|
|The specified user is not a member of that group.|USER_NOT_A_GROUP_MEMBER|400|User tried to perform a group action but the target is not in that group|

