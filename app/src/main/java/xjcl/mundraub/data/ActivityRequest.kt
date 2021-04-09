package xjcl.mundraub.data


/**
 * Master table of Activity Request codes (startActivityForResult)
 *  PlantForm 33
 *      Add/Edit marker
 *      @param nid (-1 for Add, actual nid for Edit)
 *      @return lat/lng+nid of new marker in Add case
 *  ReportPlant 35
 *      if not my plant, then ability to report, else forward to editing (PlantForm 33)
 *      @param nid
 *      @return None
 *  LocationPicker 42
 *      @param tid (icon to use) + lat/lng (position to start view)
 *      @return lat/lng (position user inputted)
 *  Login 55
 *      No I/O, this writes to the sharedPreferences object
 *  Register 56
 *      No I/O, no details stored, user sets password later anyway
 *  PlantList 60
 *      Used to reload markers if edited through the list
 *  99
 *      Result irrelevant, I just want the callback to onActivityResult
 */
enum class ActivityRequest(val value: Int) {
    PlantForm(33),
    ReportPlant(35),
    LocationPicker(42),
    Login(55),
    Register(56),
    PlantList(60),
    IRRELEVANT(99),
}
