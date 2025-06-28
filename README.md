# CS-360-Mobile-Architect-Programming-Inventory-App

## Project Summary
The Inventory Management App was developed to simplify the process of tracking, updating, and notifying users about inventory items through a clean, user-centered mobile interface. The primary goal was to assist small business owners or warehouse managers in organizing their stock by adding item details, checking quantities, and receiving SMS notifications when action is needed. This app responds to the need for a lightweight, intuitive solution for real-time inventory monitoring.

## User Needs and App Goals
This app was designed with a focus on efficiency, accessibility, and clarity. Users needed a fast and minimal method to add, view, and manage inventory, along with proactive notifications. We addressed this by ensuring seamless data entry, data viewing via a grid display, and integrating Android’s SMS permissions to simulate alert notifications.

## Key Screens and Features
To support user needs, the following core screens and features were implemented:

* Login Screen: To simulate user access control.
* Inventory Grid Screen: Displays added items in a scrollable grid format.
* Add Entry Interface: Allows input of item names and quantities.
* Check SMS Notification Button: Triggers or simulates a check for notifications.
* Navigation and Focus Grouping: Ensures ease of use and accessibility compliance.

## UI Design Considerations
User experience was prioritized by applying principles of visual hierarchy, focus order, and grouping:

* Fields and buttons were logically grouped to reduce confusion.
* A clean white background with distinct sections helped direct user attention.
* The padding and spacing adhered to Android design guidelines, making the UI intuitive.
* Fonts and button sizes were scaled appropriately for readability and touch targets.

The result was a design that minimized user effort and maximized clarity—especially important for users operating in fast-paced environments like retail or warehouses.

## Development and Coding Strategy
Development began with translating the visual wireframes from Project Two into fully functioning XML layouts. Java code was modularized, and key features were built incrementally:

* Room Database: Used for persistent local storage of inventory items.
* Permissions Handling: Integrated Manifest.permission.SEND_SMS logic.
* Event-Driven Programming: Used click listeners for all button actions.

Techniques such as fragmentation avoidance, modular coding, and comment-driven development helped maintain clarity and reusability. These strategies will be especially useful in future app development for scalability and team collaboration.

## Testing and Debugging
Testing was conducted through:

* Manual input testing for all fields and buttons.
* Logcat monitoring for RoomDB CRUD operations.
* Toast messages and alerts to confirm actions.
* SMS permission request testing across Android versions.

Testing ensured functionality was maintained throughout changes and highlighted an issue with schema exports in Room, which was resolved by setting exportSchema=false in the database annotation.

This process was critical—it ensured reliability and helped maintain user trust by catching unexpected behavior early.

## Innovation and Challenges
A key challenge was adapting the RoomDB implementation with minimal configuration while avoiding deprecated APIs. I innovated by streamlining schema configuration and handling permissions in a way that adhered to Android 31+ requirements without breaking backward compatibility.
Another challenge was managing UI responsiveness and layout integrity across different devices, which was addressed using constraint layouts and proper wrap_content/match_parent usage.

## Strength in Execution
One standout success was in integrating a clean UI with persistent data functionality using RoomDB. The synergy between the user interface and data backend showed my understanding of both front-end user-centered design and back-end data management. Additionally, my ability to simulate notification-like behavior using SMS permissions showcased my grasp of Android's ecosystem and best practices.
