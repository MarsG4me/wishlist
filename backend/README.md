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

## Current set limitations

|Type|Limiting factor|Limit|
|:---|:---|:---|
|Group creation|Num. of owned groups|5|
|Wishlist creation|Num. of owned wishlists|5|
|Wish creation|Num. of wishes in wishlist|50|


## Error Codes
Each error a logged in user may encounter will be listed below, the errors will have two fields one which is a text for the user and another representing a code that can be used in the frontend for a localized message:

|error|errId|HTTP code|reason|
|:---|:---|:---|:---|
|
|User verification failed.|USER_VERIFICATION_FAILED|500|JWT could not be used to create/verify the user in the System |
|
|Offset must be >= 0|INVALID_OFFSET|400|The provided offset is not valid and must be a number larger than 0|
|Request body is missing or malformed JSON.|MISSING_REQUEST_BODY|400|The request is missing a body or the body is not JSON|
|Not possible for yourself.|CANT_DO_SELF|400|User tried to do something which only other users can do like updating an admin state or claiming a wish|
|
|User can't create more groups.|USER_REACHED_GROUP_CREATION_LIMIT|403|User tried to create another group but already created 5|
|Group not found.|GROUP_NOT_FOUND|404|A get request for a group returned nothing|
|User is not a member of that group.|NOT_A_GROUP_MEMBER|403|User tried to access some group info without beeing part of that group|
|User is not an admin of that group.|NOT_A_GROUP_ADMIN|403|User tried to perform an action only a group admin can do|
|Can only be done by group owner.|NOT_THE_GROUP_OWNER|401|The action performed can only be done by the group owner, like managing admin permissions and deleting the group|
|The specified user is not a member of that group.|USER_NOT_A_GROUP_MEMBER|400|User tried to perform a group action but the target is not in that group|
|
|User can't create more wishlists.|USER_REACHED_WISHLIST_CREATION_LIMIT|403|User tried to create another wishlist but already created 5|
|Can only be done by wishlist owner.|NOT_THE_WISHLIST_OWNER|401|The action performed can only be done by the group owner|
|Wishlist contains claimed wishes.|WISHLIST_CONTAINS_CLAIMED_WISHES|409|The wishlist can not be fully deleted because wishes are claimed|
|No permission to access wishlist.|NOT_PERMITTED_WISHLIST|401|User tried to access a wishlist without having permission|
|
|User can't create more wishes in this wishlist.|USER_REACHED_WISH_CREATION_LIMIT|403|The user tried to add another wish to a wishlist but it already contains the max amount|
|The wish does not exist.|WISH_NOT_FOUND|404|The wish selected for the action does not exist.|
|Can only be done by wish owner.|NOT_THE_WISH_OWNER|403|The action performed can only be done by the owner of this wish.|
|
|User not found.|USER_NOT_FOUND|404|Fetching a user failed as they do not exist|
|User shares no common group.|USER_HAS_NO_COMMON_GROUP|403|The action requires that the target user has at least one common group with the requesting one.|
|
|This wish is already claimed.|ALREADY_CLAIMED|403|User tried to claim a wish but the wish is not free to claim.|
|This wish is claimed by someone else.|NOT_YOUR_CLAIM|403|User tried an action only the claimer of a wish can perform.|
