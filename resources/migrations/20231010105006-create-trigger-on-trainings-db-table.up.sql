CREATE TRIGGER tr_update_comanage_sqlsource
AFTER INSERT OR UPDATE ON trainings
FOR EACH ROW
EXECUTE FUNCTION update_comanage_sqlsource();
