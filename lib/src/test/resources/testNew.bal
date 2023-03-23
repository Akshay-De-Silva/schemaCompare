import ballerina/time;
import ballerina/persist as _;

type User record {|
    readonly string id;
    string name;
    int age;
    string email;
|};

type Post record {|
    readonly string id;
    string title;
    string body;
    time:Civil publishedAt;
|};